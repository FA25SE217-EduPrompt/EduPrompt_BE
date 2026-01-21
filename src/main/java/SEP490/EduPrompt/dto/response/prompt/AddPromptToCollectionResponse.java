package SEP490.EduPrompt.dto.response.prompt;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record AddPromptToCollectionResponse(
        UUID id,
        UUID collectionId,
        String title,
        String description,
        String visibility,
        Instant updatedAt
) {
}
