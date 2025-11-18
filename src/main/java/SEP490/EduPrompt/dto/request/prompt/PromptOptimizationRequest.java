package SEP490.EduPrompt.dto.request.prompt;

import SEP490.EduPrompt.enums.AiModel;
import jakarta.validation.constraints.*;

import java.util.UUID;

public record PromptOptimizationRequest(
        @NotNull
        UUID promptId,

        @NotNull
        AiModel aiModel,

        @NotBlank
        String optimizationInput,

        // optional
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "2.0")
        Double temperature,

        // optional
        @Min(1)
        @Max(8192)
        Integer maxTokens,

        // optional
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "1.0")
        Double topP
) {
}


