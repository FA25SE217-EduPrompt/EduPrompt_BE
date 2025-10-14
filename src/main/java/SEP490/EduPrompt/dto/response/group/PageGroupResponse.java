package SEP490.EduPrompt.dto.response.group;

import lombok.Builder;

import java.util.List;

@Builder
public record PageGroupResponse(
        List<GroupResponse> content,
        long totalElements,
        int totalPages,
        int pageNumber,
        int pageSize
) {
}