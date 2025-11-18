package SEP490.EduPrompt.dto.response.search;

import java.util.List;

public record SemanticSearchResponse(
        List<SearchResultItem> results,
        Integer totalFound,
        String searchId,
        Integer executionTimeMs
) {
}
