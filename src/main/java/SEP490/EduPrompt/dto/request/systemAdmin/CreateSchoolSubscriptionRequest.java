package SEP490.EduPrompt.dto.request.systemAdmin;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CreateSchoolSubscriptionRequest(
        Integer schoolTokenPool,
        Integer schoolTokenRemaining,
        Instant quotaResetDate,
        Instant startDate,
        Instant endDate
) {}