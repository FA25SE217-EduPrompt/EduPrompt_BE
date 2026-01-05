package SEP490.EduPrompt.dto.request.prompt;

import SEP490.EduPrompt.enums.PromptTask;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for generating prompt from file
 */
public record GeneratePromptFromFileRequest(
        @NotNull(message = "Prompt task type is required")
        PromptTask promptTask,

        String customInstruction // Optional - additional requirements from teacher
) {
}