package SEP490.EduPrompt.dto.response.group;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record CreateGroupResponse(
        UUID id,
        String name,
        UUID schoolId,
        Boolean isActive,
        Instant createdAt
) {
}