package SEP490.EduPrompt.dto.response.tag;

import lombok.Builder;

import java.util.List;

@Builder
public record PageTagResponse(
        List<TagResponse> content,
        long totalElements,
        int totalPages,
        int pageNumber,
        int pageSize
) {
}
