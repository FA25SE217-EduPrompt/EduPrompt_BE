package SEP490.EduPrompt.dto.response.prompt;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record PromptRatingResponse(
    boolean isDone
) {}