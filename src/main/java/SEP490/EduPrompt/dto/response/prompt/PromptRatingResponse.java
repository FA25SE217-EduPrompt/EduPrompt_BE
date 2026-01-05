package SEP490.EduPrompt.dto.response.prompt;

import lombok.Builder;

@Builder
public record PromptRatingResponse(
        boolean isDone
) {
}