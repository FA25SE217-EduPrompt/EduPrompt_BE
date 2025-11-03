package SEP490.EduPrompt.dto.request.prompt;

import SEP490.EduPrompt.enums.AiModel;
import jakarta.validation.constraints.*;
import lombok.Builder;

import java.util.UUID;

@Builder
public record PromptTestRequest(
        @NotNull
        UUID promptId,

        @NotNull
        AiModel aiModel,

        @NotBlank
        String inputText,

        @DecimalMin(value = "0.0")
        @DecimalMax(value = "2.0")
        Double temperature,

        @Min(1)
        @Max(8192)
        Integer maxTokens,

        @DecimalMin(value = "0.0")
        @DecimalMax(value = "1.0")
        Double topP
) {
}
