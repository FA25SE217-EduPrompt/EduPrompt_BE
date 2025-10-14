package SEP490.EduPrompt.dto.response.groupMember;

import java.util.List;

public record PageGroupMemberResponse(
        List<GroupMemberResponse> content,
        long totalElements,
        int totalPages,
        int pageNumber,
        int pageSize
) {
    public static PageGroupMemberResponse builder() {
        return new PageGroupMemberResponse(null, 0, 0, 0, 0);
    }
}
