package SEP490.EduPrompt.dto.response.groupMember;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record GroupMemberResponse(
        UUID userId,
        String firstName,
        String lastName,
        String email,
        String role,
        String status,
        Instant joinedAt
) {
}
