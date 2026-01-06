package SEP490.EduPrompt.dto.request.prompt;

import SEP490.EduPrompt.enums.OptimizationMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OptimizationRequest(
        @NotNull(message = "Prompt ID is required")
        UUID promptId,

        @NotBlank(message = "Prompt text is required")
        String promptText,

        UUID lessonId, // Optional, will be auto-detected if null

        @NotNull(message = "Optimization mode is required")
        OptimizationMode optimizationMode
) {}
