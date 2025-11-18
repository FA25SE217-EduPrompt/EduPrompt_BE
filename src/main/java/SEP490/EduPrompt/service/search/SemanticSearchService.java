package SEP490.EduPrompt.service.search;

import SEP490.EduPrompt.dto.request.search.SemanticSearchRequest;
import SEP490.EduPrompt.dto.response.search.SemanticSearchResponse;

public interface SemanticSearchService {
    /**
     * Perform semantic search for prompts
     *
     * @param request Search request with query and context
     * @return Search results with relevance scores
     */
    SemanticSearchResponse search(SemanticSearchRequest request);

    //might plan to count for search quota if user using semantic search (2 type of searching)
//    /**
//     * Get search quota remaining for user
//     * @param userId User ID
//     * @return Remaining searches for today
//     */
//    Integer getSearchQuotaRemaining(UUID userId);
}
