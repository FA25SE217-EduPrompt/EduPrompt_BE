package SEP490.EduPrompt.dto.response.payment;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record PaymentAdminListResponse(
        UUID id,
        UUID userId,
        String email,           // from User
        String fullName,        // firstName + lastName
        UUID tierId,
        String tierName,        // optional – null if no tier
        Long amount,
        String orderInfo,
        String status,
        Instant createdAt,
        Instant paidAt
) {
}
