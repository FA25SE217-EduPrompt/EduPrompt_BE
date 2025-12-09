package SEP490.EduPrompt.dto.response.payment;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record PaymentDetailedResponse(
        UUID paymentId,
        UUID userId,
        UUID tierId,
        Long amount,
        String orderInfo,
        String status,
        Instant createdAt
) {
}
