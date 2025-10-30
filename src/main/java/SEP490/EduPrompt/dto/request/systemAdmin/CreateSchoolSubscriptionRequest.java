package SEP490.EduPrompt.dto.request.systemAdmin;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CreateSchoolSubscriptionRequest(
        @NotNull UUID subscriptionTierId,
        Instant startDate,
        Instant endDate
) {}