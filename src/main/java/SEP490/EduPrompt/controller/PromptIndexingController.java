package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.search.IndexingResult;
import SEP490.EduPrompt.service.search.OperationPollingService;
import SEP490.EduPrompt.service.search.PromptIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/indexing")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class PromptIndexingController {

    private final PromptIndexingService promptIndexingService;
    private final OperationPollingService operationPollingService;

    /**
     * Index a single prompt
     * POST /api/v1/admin/indexing/prompt/{promptId}
     */
    @PostMapping("/prompt/{promptId}")
    public ResponseDto<IndexingResult> indexPrompt(
            @PathVariable UUID promptId) {

        log.info("Received request to index prompt: {}", promptId);

        IndexingResult result = promptIndexingService.indexPrompt(promptId);

        return ResponseDto.success(result);
    }

    /**
     * Reindex a prompt (delete and recreate)
     * POST /api/v1/admin/indexing/prompt/{promptId}/reindex
     */
    @PostMapping("/prompt/{promptId}/reindex")
    public ResponseDto<IndexingResult> reindexPrompt(
            @PathVariable UUID promptId) {

        log.info("Received request to reindex prompt: {}", promptId);

        IndexingResult result = promptIndexingService.reindexPrompt(promptId);

        return ResponseDto.success(result);
    }

    /**
     * Batch index all pending prompts
     * POST /api/v1/admin/indexing/batch
     */
    @PostMapping("/batch")
    public ResponseDto<List<IndexingResult>> indexAllPending() {

        log.info("Received request to batch index all pending prompts");

        List<IndexingResult> results = promptIndexingService.indexAllPendingPrompts();

//        long successCount = results.stream()
//                .filter(r -> "success".equals(r.status()))
//                .count();
        return ResponseDto.success(results);
    }

    /**
     * Remove prompt from index
     * DELETE /api/v1/admin/indexing/prompt/{promptId}
     */
    @DeleteMapping("/prompt/{promptId}")
    public ResponseDto<Void> removeFromIndex(
            @PathVariable UUID promptId) {

        log.info("Received request to remove prompt {} from index", promptId);

        promptIndexingService.removeFromIndex(promptId);

        return ResponseDto.success(null);
    }

    /**
     * Manually trigger polling for a specific prompt
     * for immediate results
     * POST /api/v1/admin/indexing/prompt/{promptId}/poll
     */
    @PostMapping("/prompt/{promptId}/poll")
    public ResponseDto<String> pollPromptOperation(
            @PathVariable UUID promptId) {

        log.info("Received request to poll operation for prompt: {}", promptId);

        boolean completed = operationPollingService.pollOperationForPrompt(promptId);

        String message = completed
                ? "Operation completed and prompt indexed successfully"
                : "Operation still processing, check again later";

        return ResponseDto.success(message);
    }

    /**
     * Manually trigger polling for all pending operations
     * POST /api/v1/admin/indexing/poll-all
     */
    @PostMapping("/poll-all")
    public ResponseDto<String> pollAllPendingOperations() {

        log.info("Received request to poll all pending operations");

        operationPollingService.pollPendingOperations();

        return ResponseDto.success("Polling completed. Check logs for results.");
    }
}