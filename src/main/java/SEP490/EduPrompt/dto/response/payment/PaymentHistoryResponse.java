package SEP490.EduPrompt.dto.response.payment;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record PaymentHistoryResponse(
        UUID id,
        String txnRef,
        Long amount,
        String status,
        Instant createdAt,
        Instant paidAt,
        UUID tierId
) {
}
