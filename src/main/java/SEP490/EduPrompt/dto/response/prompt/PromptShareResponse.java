package SEP490.EduPrompt.dto.response.prompt;

import lombok.Builder;

import java.util.UUID;

@Builder
public record PromptShareResponse (
        UUID id,
        String title,
        String description,
        String instruction,
        String context,
        String inputExample,
        String outputFormat,
        String constraints,
        UUID shareToken
){
}
