package SEP490.EduPrompt.dto.request.prompt;

import SEP490.EduPrompt.enums.AiModel;
import lombok.Builder;

import java.util.UUID;

@Builder
public record PromptTestRequest(
        UUID promptId,
        AiModel aiModel,
        String inputText,
        Double temperature,
        Integer maxTokens,
        Double topP
) {
}