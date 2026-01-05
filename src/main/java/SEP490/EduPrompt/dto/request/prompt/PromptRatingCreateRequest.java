package SEP490.EduPrompt.dto.request.prompt;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record PromptRatingCreateRequest(

        @NotNull(message = "Prompt ID is required")
        UUID promptId,

        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating cannot exceed 5")
        Short rating
) {
}