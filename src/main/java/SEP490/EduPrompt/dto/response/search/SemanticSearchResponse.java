package SEP490.EduPrompt.dto.response.search;

import lombok.Builder;

import java.util.List;

@Builder
public record SemanticSearchResponse(
        List<SearchResultItem> results,
        Integer totalFound,
        String searchId,
        Integer executionTimeMs
) {
}
