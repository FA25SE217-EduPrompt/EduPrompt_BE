package SEP490.EduPrompt.dto.request.groupMember;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.Set;
import java.util.UUID;

@Builder
public record AddGroupMembersRequest(
        @NotEmpty
        Set<MemberRequest> members
) {
    public record MemberRequest(
            @NotNull
            UUID userId
    ) {
    }
}
