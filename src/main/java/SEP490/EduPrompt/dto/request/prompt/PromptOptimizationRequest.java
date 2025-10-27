package SEP490.EduPrompt.dto.request.prompt;

import SEP490.EduPrompt.enums.AiModel;

import java.util.UUID;

public record PromptOptimizationRequest(
        UUID promptId,
        AiModel aiModel,
        String optimizationInput,
        Double temperature,
        Integer maxTokens,
        Double topP
) {
}

