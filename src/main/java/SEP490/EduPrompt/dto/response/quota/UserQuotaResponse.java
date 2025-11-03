package SEP490.EduPrompt.dto.response.quota;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record UserQuotaResponse(
        UUID userId,
        int testingQuotaRemaining,
        int testingQuotaLimit,
        int optimizationQuotaRemaining,
        int optimizationQuotaLimit,
        Instant quotaResetDate,
        Integer individualTokenLimit, Integer individualTokenRemaining
) {
}
