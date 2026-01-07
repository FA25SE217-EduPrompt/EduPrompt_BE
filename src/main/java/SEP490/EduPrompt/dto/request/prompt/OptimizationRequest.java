package SEP490.EduPrompt.dto.request.prompt;

import SEP490.EduPrompt.enums.OptimizationMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OptimizationRequest(
                // prompt id is optional for scratchpad mode
                UUID promptId,

                @NotBlank(message = "Prompt content is required") String promptContent,

                String customInstruction,

                UUID lessonId, // Optional, will be auto-detected if null

                @NotNull(message = "Optimization mode is required") OptimizationMode optimizationMode,

                Map<String, List<String>> selectedWeaknesses) {
}
