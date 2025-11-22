package SEP490.EduPrompt.dto.request.prompt;

import jakarta.validation.constraints.NotNull;

public record CreatePromptVersionRequest(
        String instruction,
        String context,
        String inputExample,
        String outputFormat,
        String constraints,

        @NotNull(message = "isAiGenerated must not be null") Boolean isAiGenerated) {
}
