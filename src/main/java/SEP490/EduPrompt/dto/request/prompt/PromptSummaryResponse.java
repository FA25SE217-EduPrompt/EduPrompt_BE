package SEP490.EduPrompt.dto.request.prompt;

import SEP490.EduPrompt.model.Tag;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PromptSummaryResponse(
        UUID id,
        String title,
        String description,
        UUID createdBy,
        Instant createdAt,
        String visibility,
        List<Tag> tags,
        UUID collectionId,
        String ownerName
) {}
