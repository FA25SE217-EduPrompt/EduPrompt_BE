package SEP490.EduPrompt.dto.response.systemAdmin;

import java.time.Instant;
import java.util.UUID;

public record SchoolSubscriptionResponse(
        UUID id,
        UUID schoolId,
        Integer tokenPool,
        Integer tokenRemaining,
        Instant startDate,
        Instant endDate,
        Instant quotaResetDate,
        boolean isActive
) {
}