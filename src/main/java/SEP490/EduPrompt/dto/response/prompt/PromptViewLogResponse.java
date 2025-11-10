package SEP490.EduPrompt.dto.response.prompt;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record PromptViewLogResponse(
        UUID id,
        UUID userId,
        UUID promptId,
        Instant createdAt
) {
}
