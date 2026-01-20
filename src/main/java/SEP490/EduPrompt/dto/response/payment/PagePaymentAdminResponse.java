package SEP490.EduPrompt.dto.response.payment;

import lombok.Builder;

import java.util.List;

@Builder
public record PagePaymentAdminResponse (
        List<PaymentAdminListResponse> content,
        long totalElements,
        int totalPages,
        int pageNumber,
        int pageSize
){
}
