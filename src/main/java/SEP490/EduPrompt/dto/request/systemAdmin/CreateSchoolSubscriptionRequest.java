package SEP490.EduPrompt.dto.request.systemAdmin;

import java.time.Instant;

public record CreateSchoolSubscriptionRequest(
        Integer schoolTokenPool,
        Integer schoolTokenRemaining,
        Instant quotaResetDate,
        Instant startDate,
        Instant endDate
) {
}