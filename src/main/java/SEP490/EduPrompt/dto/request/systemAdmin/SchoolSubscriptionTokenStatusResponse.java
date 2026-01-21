package SEP490.EduPrompt.dto.request.systemAdmin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchoolSubscriptionTokenStatusResponse {
    private UUID id;
    private UUID schoolId;
    private String schoolName;
    private Integer schoolTokenPool;
    private Integer schoolTokenRemaining;
    private Integer tokensUsed;
    private Instant quotaResetDate;
    private Boolean isActive;
    private Instant endDate;
}
