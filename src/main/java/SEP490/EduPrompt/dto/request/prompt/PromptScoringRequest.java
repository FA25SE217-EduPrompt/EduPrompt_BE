package SEP490.EduPrompt.dto.request.prompt;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record PromptScoringRequest(
        @NotBlank(message = "Prompt text is required")
        String promptText,

        UUID lessonId // Optional
) {}
