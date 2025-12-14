package SEP490.EduPrompt.service.payment;

import SEP490.EduPrompt.config.VnpayConfig;
import SEP490.EduPrompt.dto.request.payment.PaymentRequest;
import SEP490.EduPrompt.dto.response.payment.PagePaymentHistoryResponse;
import SEP490.EduPrompt.dto.response.payment.PaymentDetailedResponse;
import SEP490.EduPrompt.dto.response.payment.PaymentHistoryResponse;
import SEP490.EduPrompt.dto.response.payment.PaymentResponse;
import SEP490.EduPrompt.enums.PaymentStatus;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.Payment;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    @Override
    public String generatePaymentUrl(PaymentRequest request, HttpServletRequest servletRequest, UserPrincipal userPrincipal) {
        PayLib pay = new PayLib();
        User user = userRepo.getReferenceById(userPrincipal.getUserId());

        Payment payment = Payment.builder()
                .user(user)
                .amount(request.getAmount())
                .tier(null)
                .status(PaymentStatus.PENDING.name())
                .orderInfo(request.getOrderDescription())
                .build();

        paymentRepository.saveAndFlush(payment);
        String txnRef = userPrincipal.getUserId() + "_" + request.getSubscriptionTierId() + "_" + System.currentTimeMillis() + "_" + payment.getId();  // ← EMBED userId
        addRequestData(pay, request, servletRequest);
        pay.addRequestData("vnp_TxnRef", txnRef);
        return pay.createRequestUrl(vnpayConfig.getUrl(), vnpayConfig.getHashSecret());
    }

    @Override
    public PaymentDetailedResponse getPaymentById(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            return null;
        }
        return PaymentDetailedResponse.builder()
                .paymentId(payment.getId())
                .userId(payment.getUserId())
                .tierId(payment.getSubscriptionTierId())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .orderInfo(payment.getOrderInfo())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    // Verify Payment Response
    @Override
    @Transactional
    public PaymentResponse processVnpayReturn(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return new PaymentResponse("99", "Invalid callback");
        }

        Map<String, String> params = parseQueryString(queryString);

        String txnRef          = params.get("vnp_TxnRef");
        String responseCode    = params.getOrDefault("vnp_ResponseCode", "");
        String secureHash = params.remove("vnp_SecureHash");
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
            signData.setLength(signData.length() - 1);
        }

        boolean validSignature = validateSignature(signData.toString(), secureHash, vnpayConfig.getHashSecret());

        boolean validTmnCode = vnpayConfig.getTmnCode().equals(tmnCode);

        UUID paymentId = extractPaymentIdFromTxnRef(txnRef);

        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (!validSignature || !validTmnCode) {
            payment.setStatus(PaymentStatus.FAILED.name());
            paymentRepository.save(payment);
            return new PaymentResponse(responseCode, "Invalid signature or merchant");
        }

        if (!"00".equals(responseCode)) {
            payment.setStatus(PaymentStatus.FAILED.name());
            paymentRepository.save(payment);
            return new PaymentResponse(responseCode, "Payment failed at VNPAY");
        }

        UUID tierId = extractTierIdFromTxnRef(txnRef);
        UUID embeddedUserId = extractUserIdFromTxnRef(txnRef);
        if (embeddedUserId == null) {
            payment.setStatus(PaymentStatus.FAILED.name());
            paymentRepository.save(payment);
            return new PaymentResponse(responseCode, "User mismatch or invalid transaction");
        }

        User user = userRepo.findById(embeddedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (tierId == null) {
            payment.setStatus(PaymentStatus.FAILED.name());
            paymentRepository.save(payment);
            return new PaymentResponse(responseCode, "Cannot identify subscription tier");
        }

        Optional<SubscriptionTier> tierOpt = tierRepo.findById(tierId);
        if (tierOpt.isEmpty()) {
            payment.setStatus(PaymentStatus.FAILED.name());
            paymentRepository.save(payment);
            return new PaymentResponse(responseCode, "Tier not found");
        }
        SubscriptionTier tier = tierOpt.get();
        try {
            user.setSubscriptionTier(tier);
            userRepo.save(user);
            quotaService.syncUserQuotaWithSubscriptionTier(user.getId());
        } catch (Exception e) {
            //might need a better rollback for those quota if error
            payment.setStatus(PaymentStatus.FAILED.name());
            paymentRepository.save(payment);
            user.setSubscriptionTier(user.getSubscriptionTier());
            return new PaymentResponse(responseCode, "Failed to activate subscription");
        }
        // best case
        payment.setStatus(PaymentStatus.SUCCESS.name());
        payment.setTier(tier);
        payment.setPaidAt(Instant.now());
        paymentRepository.save(payment);
        return new PaymentResponse("00", "Confirm Success");
    }

    @Override
    public PagePaymentHistoryResponse getPaymentHistory(UserPrincipal currentUser, Pageable pageable) {
        Page<Payment> paymentPage = paymentRepository.findByUserId(currentUser.getUserId(), pageable);

        List<PaymentHistoryResponse> paymentHistoryResponses = paymentPage.getContent().stream()
                .map(payment -> PaymentHistoryResponse.builder()
                        .id(payment.getId())
                        .amount(payment.getAmount())
                        .status(payment.getStatus())
                        .createdAt(payment.getCreatedAt())
                        .paidAt(payment.getPaidAt())
                        .tierId(payment.getSubscriptionTierId())
                        .build())
                .toList();
        return PagePaymentHistoryResponse.builder()
                .content(paymentHistoryResponses)
                .pageSize(paymentPage.getSize())
                .pageNumber(paymentPage.getNumber())
                .totalPages(paymentPage.getTotalPages())
                .totalElements(paymentPage.getTotalElements())
                .build();
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
        if (parts.length < 4) return null;
        try {
            return UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private UUID extractUserIdFromTxnRef(String txnRef) {
        if (txnRef == null || txnRef.split("_").length < 4) return null;
        try {
            return UUID.fromString(txnRef.split("_")[0]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private UUID extractPaymentIdFromTxnRef(String txnRef) {
        if (txnRef == null || txnRef.split("_").length < 4) return null;
        try {
            return UUID.fromString(txnRef.split("_")[3]);
        } catch (IllegalArgumentException e) {
            return null;
        }
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

    private boolean validateSignature(String rspRaw, String inputHash, String secretKey) {
        String myChecksum = PayLib.hmacSHA512(secretKey, rspRaw);
        return myChecksum.equalsIgnoreCase(inputHash);
    }
}
