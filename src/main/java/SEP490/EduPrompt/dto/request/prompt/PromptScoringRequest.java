package SEP490.EduPrompt.dto.request.prompt;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record PromptScoringRequest(
        @NotBlank(message = "Prompt content is required")
        String promptContent,

        UUID lessonId // Optional
) {}
