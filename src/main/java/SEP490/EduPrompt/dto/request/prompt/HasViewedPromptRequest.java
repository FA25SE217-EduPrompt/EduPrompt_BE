package SEP490.EduPrompt.dto.request.prompt;

import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record HasViewedPromptRequest(
        @NotEmpty(message = "At least one Prompt ID is required")
        List<UUID> promptIds
) {
}