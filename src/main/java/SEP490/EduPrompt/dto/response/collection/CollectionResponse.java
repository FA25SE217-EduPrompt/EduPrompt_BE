package SEP490.EduPrompt.dto.response.collection;

import SEP490.EduPrompt.model.Tag;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record CollectionResponse(
        String name,
        String description,
        String visibility,
        List<Tag> tags,
        Instant createdAt
) {
}