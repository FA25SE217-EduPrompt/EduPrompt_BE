package SEP490.EduPrompt.dto.response.quota;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record UserQuotaResponse(
        UUID userId,
        Integer testingQuotaLimit,
        Integer testingQuotaRemaining,
        Integer optimizationQuotaLimit,
        Integer optimizationQuotaRemaining,
        Integer individualTokenLimit,
        Integer individualTokenRemaining,
        Integer promptActionLimit,
        Integer promptActionRemaining,
        Integer collectionActionLimit,
        Integer collectionActionRemaining,
        Integer promptUnlockLimit,
        Integer promptUnlockRemaining,
        Instant quotaResetDate
) {
}
