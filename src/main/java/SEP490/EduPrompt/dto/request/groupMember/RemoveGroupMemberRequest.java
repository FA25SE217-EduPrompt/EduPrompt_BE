package SEP490.EduPrompt.dto.request.groupMember;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record RemoveGroupMemberRequest(
        @NotNull
        UUID userId
) {
}
