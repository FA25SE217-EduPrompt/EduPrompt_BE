package SEP490.EduPrompt.service.search;

import SEP490.EduPrompt.dto.response.search.FileUploadResponse;
import SEP490.EduPrompt.dto.response.search.IndexingResult;
import SEP490.EduPrompt.enums.Visibility;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.exception.client.GeminiApiException;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.repo.PromptRepository;
import com.google.genai.errors.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptIndexingServiceImpl implements PromptIndexingService {

    private static final int BATCH_DELAY_MS = 1000;
    private static final int MAX_RETRIES = 3;
    private final PromptRepository promptRepository;
    private final GeminiClientService geminiClientService;

    @Value("${gemini.file-search-store}")
    private String fileSearchStoreName;

    @Override
    @Transactional
    public IndexingResult indexPrompt(UUID promptId) {
        log.info("Starting indexing for prompt: {}", promptId);

        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("prompt not found"));

        // Validate prompt is indexable
        IndexingResult validationResult = validatePromptForIndexing(prompt);
        if (validationResult != null) {
            return validationResult;
        }

        try {
            // Upload to Gemini
            FileUploadResponse uploadResponse = geminiClientService.uploadToFileSearchStore(fileSearchStoreName, prompt);

            // Update prompt with file ID
            prompt.setGeminiFileId(uploadResponse.operationId()); //it should be document id, yet since the response from gemini is async, the document id within that time will be null, i've tested this
            // check this id format to verify its status , using polling method to check
            prompt.setLastIndexedAt(Instant.now());
            prompt.setIndexingStatus("indexed");
            promptRepository.save(prompt);

            log.info("Successfully indexed prompt: {} with file ID: {}",
                    promptId, uploadResponse.documentId());

            return IndexingResult.builder()
                    .promptId(promptId)
                    .status("success")
                    .documentId(uploadResponse.documentId())
                    .build();

        } catch (GeminiApiException e) {
            log.error("Gemini API error indexing prompt: {}", promptId, e);

            prompt.setIndexingStatus("failed");
            promptRepository.save(prompt);

            return IndexingResult.builder()
                    .promptId(promptId)
                    .status("failed")
                    .errorMessage("Gemini API error: " + e.getMessage())
                    .build();

        } catch (ClientException e) {
            log.error("Unexpected error indexing prompt: {}", promptId, e);

            prompt.setIndexingStatus("failed");
            promptRepository.save(prompt);

            return IndexingResult.builder()
                    .promptId(promptId)
                    .status("failed")
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    @Transactional
    public IndexingResult reindexPrompt(UUID promptId) {
        log.info("Starting reindexing for prompt: {}", promptId);

        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("prompt not found"));

        if (prompt.getGeminiFileId() != null) {
            try {
                geminiClientService.deleteDocument(prompt.getGeminiFileId());
                log.info("Deleted old file {} for prompt {}",
                        prompt.getGeminiFileId(), promptId);
            } catch (Exception e) {
                log.warn("Failed to delete old file {}: {}",
                        prompt.getGeminiFileId(), e.getMessage());
            }
        }

        prompt.setGeminiFileId(null);
        prompt.setIndexingStatus("pending");
        promptRepository.save(prompt);

        return indexPrompt(promptId);
    }

    @Override
    @Transactional
    public List<IndexingResult> indexAllPendingPrompts() {
        log.info("Starting batch indexing of pending prompts");

        List<Prompt> pendingPrompts = promptRepository.findByIndexingStatusAndIsDeleted("pending", false);

        log.info("Found {} prompts to index", pendingPrompts.size());

        List<IndexingResult> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;

        for (Prompt prompt : pendingPrompts) {
            // Only index non-private prompts
            if (!Visibility.PRIVATE.name().equalsIgnoreCase(prompt.getVisibility())) {
                IndexingResult result = indexPromptWithRetry(prompt.getId());
                results.add(result);

                if ("success".equalsIgnoreCase(result.status())) {
                    successCount++;
                } else if ("failed".equalsIgnoreCase(result.status())) {
                    failedCount++;
                } else {
                    skippedCount++;
                }

                // Add delay to avoid rate limiting
                if (results.size() < pendingPrompts.size()) {
                    try {
                        Thread.sleep(BATCH_DELAY_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Batch indexing interrupted");
                        break;
                    }
                }
            } else {
                results.add(IndexingResult.builder()
                        .promptId(prompt.getId())
                        .status("skipped")
                        .errorMessage("Private prompts are not indexed")
                        .build());
                skippedCount++;
            }
        }

        log.info("Batch indexing complete. Total: {}, Success: {}, Failed: {}, Skipped: {}",
                results.size(), successCount, failedCount, skippedCount);

        return results;
    }

    @Override
    @Transactional
    public void removeFromIndex(UUID promptId) {
        log.info("Removing prompt {} from index", promptId);

        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("prompt not found"));

        if (prompt.getGeminiFileId() != null) {
            try {
                geminiClientService.deleteDocument(prompt.getGeminiFileId());

                prompt.setGeminiFileId(null);
                prompt.setIndexingStatus("pending");
                promptRepository.save(prompt);

                log.info("Successfully removed prompt {} from index", promptId);
            } catch (Exception e) {
                log.error("Failed to remove prompt {} from index: {}", promptId, e.getMessage());
                throw new GeminiApiException("Failed to remove from index", e);
            }
        } else {
            log.info("Prompt {} has no file ID, nothing to remove", promptId);
        }
    }

    /**
     * Validate if prompt can be indexed
     */
    private IndexingResult validatePromptForIndexing(Prompt prompt) {
        // Skip private prompts
        if (Visibility.PRIVATE.name().equalsIgnoreCase(prompt.getVisibility())) {
            log.info("Skipping private prompt: {}", prompt.getId());
            return IndexingResult.builder()
                    .promptId(prompt.getId())
                    .status("skipped")
                    .errorMessage("Private prompts are not indexed")
                    .build();
        }

        if (prompt.getIsDeleted()) {
            log.info("Skipping deleted prompt: {}", prompt.getId());
            return IndexingResult.builder()
                    .promptId(prompt.getId())
                    .status("skipped")
                    .errorMessage("Deleted prompts are not indexed")
                    .build();
        }

        // Skip prompts without content
        if ((prompt.getTitle() == null || prompt.getTitle().isBlank()) &&
                (prompt.getDescription() == null || prompt.getDescription().isBlank()) &&
                (prompt.getInstruction() == null || prompt.getInstruction().isBlank())) {
            log.info("Skipping empty prompt: {}", prompt.getId());
            return IndexingResult.builder()
                    .promptId(prompt.getId())
                    .status("skipped")
                    .errorMessage("Prompt has no content to index")
                    .build();
        }

        return null;
    }

    /**
     * Index prompt with retry logic
     */
    private IndexingResult indexPromptWithRetry(UUID promptId) {
        IndexingResult lastResult = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                //should use transaction template here
                lastResult = indexPrompt(promptId);

                if ("success".equals(lastResult.status())) {
                    return lastResult;
                }

                log.warn("Indexing attempt {}/{} failed for prompt {}",
                        attempt, MAX_RETRIES, promptId);

                if (attempt < MAX_RETRIES) {
                    Thread.sleep(2000 * attempt);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in retry attempt {}/{} for prompt {}: {}",
                        attempt, MAX_RETRIES, promptId, e.getMessage());
            }
        }

        return lastResult != null ? lastResult : IndexingResult.builder()
                .promptId(promptId)
                .status("failed")
                .errorMessage("Failed after " + MAX_RETRIES + " retries")
                .build();
    }
}
