package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.response.quota.UserQuotaResponse;
import SEP490.EduPrompt.enums.QuotaType;

import java.util.UUID;

public interface QuotaService {
    UserQuotaResponse getUserQuota(UUID userId);

    void validateQuota(UUID userId, QuotaType quotaType, int estimatedToken);

    void decrementQuota(UUID userId, QuotaType quotaType, int tokenUsed);

    void validateAndDecrementQuota(UUID userId, QuotaType quotaType, int tokenUsed);

    void resetExpiredQuotas();

    void syncUserQuotaWithSubscriptionTier(UUID userId, UUID subscriptionTierId);
}
