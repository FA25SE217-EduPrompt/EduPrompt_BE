package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.payment.PaymentRequest;
import SEP490.EduPrompt.dto.response.ErrorMessage;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.payment.PaymentResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.payment.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

import static SEP490.EduPrompt.util.SecurityUtil.getClientIp;

@RestController
@RequestMapping("/api/payments/vnpay")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/vnpay")
    @PreAuthorize("hasAnyRole('TEACHER', 'SYSTEM_ADMIN')")  // ← Add auth if not already
    public ResponseDto<String> createPayment(
            @RequestBody PaymentRequest request,
            HttpServletRequest servletRequest,
            @AuthenticationPrincipal UserPrincipal userPrincipal  // ← ADD THIS
    ) {
        try {
            String paymentUrl = paymentService.generatePaymentUrl(request, servletRequest, userPrincipal);
            return ResponseDto.success(paymentUrl);
        } catch (Exception e) {
            return ResponseDto.error("500", "Error generating payment URL: " + e.getMessage());
        }
    }

    @GetMapping("/vnpay-return")
    @PreAuthorize("permitAll()")
    public ResponseDto<PaymentResponse> handlePaymentResponse(
            HttpServletRequest servletRequest,  // ← ADD THIS
            @AuthenticationPrincipal(expression = "#this == null ? null : this")
            UserPrincipal userPrincipal) {

        String queryString = servletRequest.getQueryString();  // ← USE THIS

        PaymentResponse result = paymentService.processVnpayReturn(
                queryString,
                userPrincipal
        );

        if (result.isSuccess()) {
            return ResponseDto.success(result);
        } else {
            return ResponseDto.error("400", result.getMessage());
        }
    }
}
