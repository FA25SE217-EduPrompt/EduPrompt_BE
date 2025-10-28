package SEP490.EduPrompt.dto.response.prompt;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record PromptVersionResponse(
        UUID id,
        UUID promptId,
        String instruction,
        String context,
        String inputExample,
        String outputFormat,
        String constraints,
        UUID editorId,
        Integer versionNumber,
        Boolean isAiGenerated,
        Instant createdAt
) {}
