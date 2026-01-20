package SEP490.EduPrompt.dto.request.prompt;

import lombok.Builder;

import java.util.UUID;

@Builder
public record AddPromptToCollectionRequest(
        UUID promptId,
        UUID collectionId
) {
}
