package SEP490.EduPrompt.dto.response.school;

import java.time.Instant;
import java.util.UUID;

public record SchoolEmailResponse(
        UUID id,
        String email,
        Instant createdAt
) {}