package SEP490.EduPrompt.dto.request.prompt;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record CreatePromptViewLogRequest(
        @NotNull(message = "Prompt ID is required")
        UUID promptId
) {
}
