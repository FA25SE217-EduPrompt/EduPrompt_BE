package SEP490.EduPrompt.dto.response.prompt;

import lombok.Builder;

import java.time.Instant;

@Builder
public record ClientPromptResponse(
        String content,
        String prompt,
        String model,
        Double temperature,
        Integer maxTokens,
        Double topP,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String finishReason,
        String id, //from openai id field
        String apiRequestId,
        Instant createdAt
) {
}
