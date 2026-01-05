package SEP490.EduPrompt.dto.response.prompt;

import SEP490.EduPrompt.model.Tag;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record PromptAllResponse(
        UUID id,
        UUID userId,
        UUID collectionId,
        String title,
        String description,
        String instruction,
        String context,
        String inputExample,
        String outputFormat,
        String constraints,
        String visibility,
        UUID createdBy,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt,
        Boolean isDeleted,
        Instant deletedAt,
        UUID currentVersionId,
        Double avgRating,
        String geminiFileId,
        Instant lastIndexedAt,
        String indexingStatus,
        List<Tag> tags,
        UUID shareToken
) {
}
