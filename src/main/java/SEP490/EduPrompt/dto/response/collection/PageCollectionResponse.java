package SEP490.EduPrompt.dto.response.collection;

import lombok.Builder;

import java.util.List;

@Builder
public record PageCollectionResponse(
        List<CollectionResponse> content,
        long totalElements,
        long totalPages,
        int pageNumber,
        int pageSize
) {
}
