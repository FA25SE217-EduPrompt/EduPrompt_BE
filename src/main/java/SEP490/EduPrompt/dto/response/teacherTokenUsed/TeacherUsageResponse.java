package SEP490.EduPrompt.dto.response.teacherTokenUsed;

import java.time.Instant;
import java.util.UUID;

public record TeacherUsageResponse(
        UUID userId,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        Instant createdAt,
        Long schoolTokensUsed,
        Integer individualTokensUsed
) {
}
