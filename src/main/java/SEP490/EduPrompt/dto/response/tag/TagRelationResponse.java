package SEP490.EduPrompt.dto.response.tag;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record TagRelationResponse(
        UUID tagId,
        Instant createdAt
) {
}
