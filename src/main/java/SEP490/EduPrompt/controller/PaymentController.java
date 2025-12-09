package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.payment.PaymentRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.payment.PagePaymentHistoryResponse;
import SEP490.EduPrompt.dto.response.payment.PaymentDetailedResponse;
import SEP490.EduPrompt.dto.response.payment.PaymentResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.payment.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments/vnpay")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/vnpay")
    @PreAuthorize("hasAnyRole('TEACHER', 'SYSTEM_ADMIN')")
    public ResponseDto<String> createPayment(
            @RequestBody PaymentRequest request,
            HttpServletRequest servletRequest,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        try {
            String paymentUrl = paymentService.generatePaymentUrl(request, servletRequest, userPrincipal);
            return ResponseDto.success(paymentUrl);
        } catch (Exception e) {
            return ResponseDto.error("500", "Error generating payment URL: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<PaymentDetailedResponse> getPayment(@PathVariable UUID id) {
        return ResponseDto.success(paymentService.getPaymentById(id));
    }


    @GetMapping("/vnpay-return")
    public ResponseDto<PaymentResponse> handlePaymentResponse(
            HttpServletRequest servletRequest) {

        String queryString = servletRequest.getQueryString();

        PaymentResponse result = paymentService.processVnpayReturn(
                queryString
        );

        if (result.isSuccess()) {
            return ResponseDto.success(result);
        } else {
            return ResponseDto.error("400", result.getMessage());
        }
    }

    @GetMapping("/my-payment")
    @PreAuthorize("hasAnyRole('TEACHER', 'SYSTEM_ADMIN')")
    public ResponseDto<PagePaymentHistoryResponse> getMyPrompts(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Retrieving private prompts for user: {}", currentUser.getUserId());
        Pageable pageable = PageRequest.of(page, size);
        return ResponseDto.success(paymentService.getPaymentHistory(currentUser, pageable));
    }
}
