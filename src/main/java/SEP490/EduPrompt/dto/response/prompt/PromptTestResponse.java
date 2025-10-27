package SEP490.EduPrompt.dto.response.prompt;

import SEP490.EduPrompt.enums.AiModel;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record PromptTestResponse(
        UUID id,
        UUID promptId,
        AiModel aiModel,
        String inputText,
        String output,
        Integer tokensUsed,
        Integer executionTimeMs,
        Double temperature,
        Integer maxTokens,
        Double topP,
        Instant createdAt
) {
}
