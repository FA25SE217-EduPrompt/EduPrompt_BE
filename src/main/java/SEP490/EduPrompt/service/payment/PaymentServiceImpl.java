package SEP490.EduPrompt.service.payment;

import SEP490.EduPrompt.config.VnpayConfig;
import SEP490.EduPrompt.dto.request.payment.PaymentRequest;
import SEP490.EduPrompt.dto.response.payment.PaymentResponse;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.SubscriptionTier;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.repo.PaymentRepository;
import SEP490.EduPrompt.repo.SubscriptionTierRepository;
import SEP490.EduPrompt.repo.UserRepository;
import SEP490.EduPrompt.service.ai.QuotaService;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.util.PayLib;
import SEP490.EduPrompt.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final VnpayConfig vnpayConfig;
    private final PaymentRepository paymentRepository;
    private final SubscriptionTierRepository tierRepo;
    private final UserRepository userRepo;
    private final QuotaService quotaService;

    // Generate Payment URL
    public String generatePaymentUrl(PaymentRequest request, HttpServletRequest servletRequest, UserPrincipal userPrincipal) {
        PayLib pay = new PayLib();
        String txnRef = userPrincipal.getUserId() + "_" + request.getSubscriptionTierId() + "_" + System.currentTimeMillis();  // ← EMBED userId
        addRequestData(pay, request, servletRequest);
        pay.addRequestData("vnp_TxnRef", txnRef);  // Override with new txnRef
        return pay.createRequestUrl(vnpayConfig.getUrl(), vnpayConfig.getHashSecret());
    }

    private void addRequestData(PayLib pay, PaymentRequest request, HttpServletRequest servletRequest) {
        String txnRef = request.getSubscriptionTierId() + "_" + System.currentTimeMillis();

        pay.addRequestData("vnp_Version", vnpayConfig.getVersion() != null ? vnpayConfig.getVersion() : "2.1.0");
        pay.addRequestData("vnp_Command", "pay");
        pay.addRequestData("vnp_TmnCode", vnpayConfig.getTmnCode());
        pay.addRequestData("vnp_Amount", String.valueOf(request.getAmount() * 100));
        pay.addRequestData("vnp_BankCode", request.getBankCode() != null ? request.getBankCode() : "");
        pay.addRequestData("vnp_CreateDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        pay.addRequestData("vnp_CurrCode", "VND");

        // Use real client IP from headers (supports proxy, Cloudflare, Nginx, etc.)
        pay.addRequestData("vnp_IpAddr", SecurityUtil.getClientIp(servletRequest));

        pay.addRequestData("vnp_Locale", "vn");
        pay.addRequestData("vnp_OrderInfo", request.getOrderDescription());
        pay.addRequestData("vnp_OrderType", "billpayment");
        pay.addRequestData("vnp_ReturnUrl", vnpayConfig.getReturnUrl());
        pay.addRequestData("vnp_TxnRef", txnRef);
    }

    // Verify Payment Response
    @Override
    public PaymentResponse processVnpayReturn(String queryString, UserPrincipal currentUser) {
        if (currentUser == null) {
            return new PaymentResponse(false, "User not authenticated", "99");
        }
        if (queryString == null || queryString.isEmpty()) {
            return new PaymentResponse(false, "Invalid callback", "99");
        }

        Map<String, String> params = parseQueryString(queryString);

        String txnRef          = params.get("vnp_TxnRef");
        String responseCode    = params.getOrDefault("vnp_ResponseCode", "");
        String secureHash      = params.remove("vnp_SecureHash");  // ← REMOVE to exclude from signing
        String tmnCode         = params.get("vnp_TmnCode");

        // 1. Rebuild sorted sign data (order-independent)
        StringBuilder signData = new StringBuilder();
        SortedMap<String, String> sortedParams = new TreeMap<>(params);
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                signData.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append("=")
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                        .append("&");
            }
        }
        if (!signData.isEmpty()) {
            signData.setLength(signData.length() - 1);  // Remove trailing '&'
        }

        boolean validSignature = validateSignature(signData.toString(), secureHash, vnpayConfig.getHashSecret());

        // 2. Verify merchant code
        boolean validTmnCode = vnpayConfig.getTmnCode().equals(tmnCode);

        if (!validSignature || !validTmnCode) {
            return new PaymentResponse(false, "Invalid signature or merchant", responseCode);
        }

        if (!"00".equals(responseCode)) {
            return new PaymentResponse(false, "Payment failed at VNPAY", responseCode);
        }

        // 3. Extract subscriptionTierId from vnp_TxnRef → format: tierUuid_timestamp
        UUID tierId = extractTierIdFromTxnRef(txnRef);
        UUID embeddedUserId = extractUserIdFromTxnRef(txnRef);
        if (embeddedUserId == null || !embeddedUserId.equals(currentUser.getUserId())) {
            return new PaymentResponse(false, "User mismatch or invalid transaction", responseCode);
        }

        User user = userRepo.findById(embeddedUserId)  // Use embedded ID
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (tierId == null) {
            return new PaymentResponse(false, "Cannot identify subscription tier", responseCode);
        }

        // 4. Validate tier exists
        if (tierRepo.findById(tierId).isEmpty()) {
            return new PaymentResponse(false, "Subscription tier not found", responseCode);
        }
        Optional<SubscriptionTier> tierOpt = tierRepo.findById(tierId);
        if (tierOpt.isEmpty()) {
            return new PaymentResponse(false, "Tier not found", responseCode);
        }
        SubscriptionTier tier = tierOpt.get();
        BigDecimal priceBd = BigDecimal.valueOf(tier.getPrice());
        long expectedAmount = priceBd.multiply(BigDecimal.valueOf(100)).longValueExact();

        long returnedAmount = Long.parseLong(params.getOrDefault("vnp_Amount", "0"));
        if (returnedAmount != expectedAmount) {
            return new PaymentResponse(false, "Amount mismatch", responseCode);
        }

        // 5. APPLY SUBSCRIPTION – this is the real business action
        try {
            user.setSubscriptionTier(tierRepo.findById(tierId).get());
            userRepo.save(user);
            quotaService.syncUserQuotaWithSubscriptionTier(currentUser.getUserId());
        } catch (Exception e) {
            // Log this in real project
            return new PaymentResponse(false, "Failed to activate subscription", responseCode);
        }

        // SUCCESS
        return new PaymentResponse(true, "Subscription activated successfully", tierId);
    }

    //====================HELPER==================
    private Map<String, String> parseQueryString(String queryString) {
        Map<String, String> params = new HashMap<>();
        if (queryString == null) return params;
        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                params.put(URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8));
            }
        }
        return params;
    }

    private UUID extractTierIdFromTxnRef(String txnRef) {
        if (txnRef == null || !txnRef.contains("_")) {
            return null;
        }
        String[] parts = txnRef.split("_");
        if (parts.length < 3) return null;
        try {
            return UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private UUID extractUserIdFromTxnRef(String txnRef) {
        if (txnRef == null || txnRef.split("_").length < 3) return null;
        try {
            return UUID.fromString(txnRef.split("_")[0]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean validateSignature(String rspRaw, String inputHash, String secretKey) {
        String myChecksum = PayLib.hmacSHA512(secretKey, rspRaw);
        return myChecksum.equalsIgnoreCase(inputHash);
    }
}
