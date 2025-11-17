package SEP490.EduPrompt.dto.response.schoolAdmin;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record SchoolSubscriptionUsageResponse(
        UUID subscriptionId,
        String schoolName,
        Integer totalTokenPool,
        Long tokenUsed,
        Integer tokenRemaining,
        Instant startDate,
        Instant endDate,
        Instant quotaResetDate,
        boolean isActive,
        int teacherCount
) {
}
