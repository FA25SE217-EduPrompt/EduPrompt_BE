package SEP490.EduPrompt.dto.response.payment;

import lombok.Builder;

@Builder
public record MonthlyPaymentSummaryResponse(
        Integer year,
        Integer month,
        String monthName,
        Long totalAmount,
        Long totalTransactions,
        Long successfulCount,
        Long pendingCount,
        Long failedCount,
        Double averageAmount
) {
}
