package SEP490.EduPrompt.service.search;

import SEP490.EduPrompt.dto.response.search.IndexingResult;

import java.util.List;
import java.util.UUID;

public interface PromptIndexingService {

    /**
     * Index a single prompt to Gemini File Search
     * Called when prompt is created or updated
     */
    IndexingResult indexPrompt(UUID promptId);

    /**
     * Reindex a prompt (delete old file and upload new one)
     */
    IndexingResult reindexPrompt(UUID promptId);

    /**
     * Batch index all unindexed prompts
     * Used for initial setup or bulk operations
     */
    List<IndexingResult> indexAllPendingPrompts();

    /**
     * Remove prompt from Gemini File Search
     * Called when prompt is deleted or made private
     */
    void removeFromIndex(UUID promptId);
}