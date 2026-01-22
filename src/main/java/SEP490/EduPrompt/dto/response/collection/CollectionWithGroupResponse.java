package SEP490.EduPrompt.dto.response.collection;

import SEP490.EduPrompt.model.Tag;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record CollectionWithGroupResponse(
        UUID id,
        UUID groupId,
        String name,
        String description,
        String visibility,
        Instant createdAt
) {
}
