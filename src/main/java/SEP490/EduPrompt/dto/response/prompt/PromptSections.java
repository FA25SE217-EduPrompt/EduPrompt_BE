package SEP490.EduPrompt.dto.response.prompt;

import java.time.Instant;

/**
 * Internal DTO for parsing AI response
 */
public record PromptSections(
        String instruction,
        String context,
        String input_example,
        String output_format,
        String constraints
) {
    public GeneratePromptFromFileResponse toResponse(
            String aiModel,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens) {
        return new GeneratePromptFromFileResponse(
                instruction,
                context,
                input_example,
                output_format,
                constraints,
                aiModel,
                promptTokens,
                completionTokens,
                totalTokens,
                Instant.now()
        );
    }
}