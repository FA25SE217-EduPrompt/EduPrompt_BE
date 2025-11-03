package SEP490.EduPrompt.dto.request.prompt;

import SEP490.EduPrompt.enums.AiModel;

import java.util.UUID;

public record PromptTestRequest(
        UUID promptId,
        AiModel aiModel,
        String inputText,
        Double temperature,
        Integer maxTokens,
        Double topP
) {
}