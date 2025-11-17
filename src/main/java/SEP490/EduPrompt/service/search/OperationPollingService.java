package SEP490.EduPrompt.service.search;

import SEP490.EduPrompt.dto.response.search.ImportOperationResponse;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.repo.PromptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationPollingService {

    private static final int MAX_POLL_ATTEMPTS = 3;
    private static final String OPERATION_NAME_FACTOR = "operations/";
    private static final String DOCUMENT_NAME_FACTOR = "documents/";
    private final PromptRepository promptRepository;
    private final GeminiClientService geminiClientService;

    /**
     * Scheduled job to poll pending import operations
     * Runs every 3 minute since this job is not necessarily processed in real time
     */
    @Scheduled(fixedDelay = 180000, initialDelay = 60000)
    @Transactional
    public void pollPendingOperations() {
        List<Prompt> pendingPrompts = promptRepository
                .findByIndexingStatusAndIsDeleted("pending", false);

        if (pendingPrompts.isEmpty()) {
            return;
        }

        log.info("Found {} prompts with pending operations to poll", pendingPrompts.size());

        int successCount = 0;
        int stillProcessingCount = 0;
        int failedCount = 0;
        int retryCount = 0;
        for (Prompt prompt : pendingPrompts) {
            try {
                boolean completed = checkAndUpdateOperationStatus(prompt);
                if (completed) {
                    successCount++;
                } else {
                    stillProcessingCount++;
                }

            } catch (Exception e) {
                log.error("Error polling operation for prompt {}: {}",
                        prompt.getId(), e.getMessage());

                retryCount++;

                if (retryCount >= MAX_POLL_ATTEMPTS) {
                    log.error("Max poll attempts ({}) reached for prompt {}, marking as failed",
                            MAX_POLL_ATTEMPTS, prompt.getId());
                    prompt.setIndexingStatus("failed");
                    failedCount++;
                }

                promptRepository.save(prompt);
            }
        }

        log.info("Polling complete. Success: {}, Still processing: {}, Failed: {}",
                successCount, stillProcessingCount, failedCount);
    }

    /**
     * Check operation status and update prompt accordingly
     */
    @Transactional
    public boolean checkAndUpdateOperationStatus(Prompt prompt) {
        if (prompt.getGeminiFileId() == null) {
            log.info("Prompt {} has no operation ID to poll", prompt.getId());
            return false;
        }
        if (prompt.getGeminiFileId().contains(DOCUMENT_NAME_FACTOR)) {
            prompt.setIndexingStatus("indexed");
            promptRepository.save(prompt);
            log.info("Prompt {} has already uploaded with document name : {}", prompt.getId(), prompt.getGeminiFileId());
            return false;
        }

        log.debug("Polling operation {} for prompt {}",
                prompt.getGeminiFileId(), prompt.getId());

        ImportOperationResponse operationResponse =
                geminiClientService.pollOperation(prompt.getGeminiFileId());

        if (operationResponse.done()) {
            String documentId = extractDocumentId(operationResponse);

            if (documentId != null) {
                log.info("Operation completed for prompt {}. Document ID: {}",
                        prompt.getId(), documentId);

                prompt.setGeminiFileId(documentId);
                prompt.setIndexingStatus("indexed");
                prompt.setLastIndexedAt(Instant.now());

                log.info("Successfully indexed prompt {} with document {}",
                        prompt.getId(), documentId);
            }
        } else {
            // Still processing
            log.debug("Operation {} still processing for prompt {}",
                    prompt.getGeminiFileId(), prompt.getId());
            return false;
        }

        promptRepository.save(prompt);
        return true;
    }

    /**
     * Extract document ID from operation response
     * The document ID is embedded in the operation metadata
     */
    private String extractDocumentId(ImportOperationResponse operationResponse) {
        // Format: fileSearchStores/{store}/documents/{doc}
        String documentId = operationResponse.documentId();

        if (documentId != null && documentId.contains(DOCUMENT_NAME_FACTOR)) {
            return documentId;
        }

        // maybe try to extract from operation name
        // operation name format: fileSearchStores/{store}/operations/{op}
        // we need to convert this to document ID
        log.info("Could not extract document ID from operation response: {}",
                operationResponse);

        return null;
    }

    /**
     * Manual trigger to check a specific prompt's operation
     * Useful for testing or admin operations
     */
    @Transactional
    public void pollOperationForPrompt(String promptId) {
        Prompt prompt = promptRepository.findById(java.util.UUID.fromString(promptId))
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found"));

        checkAndUpdateOperationStatus(prompt);
    }
}
