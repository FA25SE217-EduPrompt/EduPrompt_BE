package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.enums.QuotaType;

import java.util.UUID;

public interface QuotaService {
    //check remaining, auto-reset if needed, decrement quota
    void validateAndDecrementQuota(UUID userId, QuotaType quotaType);


}
