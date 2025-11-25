package SEP490.EduPrompt.dto.response.payment;

import lombok.Builder;

import java.util.List;

@Builder
public record PagePaymentHistoryResponse (
        List<PaymentHistoryResponse> content,
        long totalElements,
        int totalPages,
        int pageNumber,
        int pageSize
){
}
