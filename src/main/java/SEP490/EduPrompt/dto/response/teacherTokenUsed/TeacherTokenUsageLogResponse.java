package SEP490.EduPrompt.dto.response.teacherTokenUsed;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Builder
public record TeacherTokenUsageLogResponse (
        UUID id,
        UUID schoolSubscriptionId,
        UUID subscriptionTierId,
        UUID userId,
        Integer tokensUsed,
        Instant usedAt
){
}
