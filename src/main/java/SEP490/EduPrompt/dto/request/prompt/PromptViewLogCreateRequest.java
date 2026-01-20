package SEP490.EduPrompt.dto.request.prompt;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record PromptViewLogCreateRequest (
        @NotEmpty(message = "At least one Prompt ID is required")
        List<@NotNull UUID> promptIds
){
}
