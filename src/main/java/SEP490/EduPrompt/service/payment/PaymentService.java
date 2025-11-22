package SEP490.EduPrompt.service.payment;

import SEP490.EduPrompt.dto.request.payment.PaymentRequest;
import SEP490.EduPrompt.dto.response.payment.PaymentResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.UUID;

public interface PaymentService {
    String generatePaymentUrl(PaymentRequest request, HttpServletRequest servletRequest, UserPrincipal userPrincipal);

    PaymentResponse processVnpayReturn(String queryString, UserPrincipal currentUser);

    boolean validateSignature(String rspRaw, String inputHash, String secretKey);
}
