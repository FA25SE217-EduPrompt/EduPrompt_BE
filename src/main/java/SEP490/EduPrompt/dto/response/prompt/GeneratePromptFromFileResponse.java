package SEP490.EduPrompt.dto.response.prompt;

import java.time.Instant;

/**
 * Response DTO containing the 5 structured prompt sections
 */
public record GeneratePromptFromFileResponse(
        String instruction,
        String context,
        String inputExample,
        String outputFormat,
        String constraints,

        // Metadata
        String aiModel,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Instant createdAt
) {
}
