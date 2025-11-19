package SEP490.EduPrompt.service.search;

import SEP490.EduPrompt.dto.response.search.ImportOperationResponse;
import SEP490.EduPrompt.enums.Visibility;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationPollingService {

    private static final int MAX_POLL_ATTEMPTS = 3;
    private static final String OPERATION_NAME_FACTOR = "operations/";
    private static final String DOCUMENT_NAME_FACTOR = "documents/";
    private final PromptRepository promptRepository;
    private final GeminiClientService geminiClientService;

    private final Map<UUID, Integer> retryTracking = new ConcurrentHashMap<>();

    /**
     * Scheduled job to poll pending import operations
     * Runs every 3 minute since this job is not necessarily processed in real time
     */
    @Scheduled(fixedDelay = 180000, initialDelay = 60000)
    @Transactional
    public void pollPendingOperations() {
        //public prompt only
        List<Prompt> pendingPrompts = promptRepository
                .findByIndexingStatusAndIsDeletedAndVisibility(
                        "pending",
                        false,
                        Visibility.PUBLIC.name());

        if (pendingPrompts.isEmpty()) {
            log.info("No pending public prompts to poll");
            return;
        }

        log.info("Found {} prompts with pending operations to poll", pendingPrompts.size());

        int successCount = 0;
        int stillProcessingCount = 0;
        int failedCount = 0;

        for (Prompt prompt : pendingPrompts) {
            try {
                boolean completed = checkAndUpdateOperationStatus(prompt);

                if (completed) {
                    successCount++;
                    retryTracking.remove(prompt.getId());
                } else {
                    stillProcessingCount++;
                }

            } catch (Exception e) {
                log.error("Error polling operation for prompt {}: {}", prompt.getId(), e.getMessage());
                int currentCount = retryTracking.getOrDefault(prompt.getId(), 0);

                currentCount++;

                if (currentCount >= MAX_POLL_ATTEMPTS) {
                    //mark as failed
                    log.error("Max poll attempts ({}) reached for prompt {}, marking as failed",
                            MAX_POLL_ATTEMPTS, prompt.getId());
                    prompt.setIndexingStatus("failed");
                    promptRepository.save(prompt);

                    failedCount++;
                    retryTracking.remove(prompt.getId());
                } else {
                    // retry
                    retryTracking.put(prompt.getId(), currentCount);
                }
            }
        }

        log.info("Polling complete. Success: {}, Still processing: {}, Failed: {}",
                successCount, stillProcessingCount, failedCount);
    }

    /**
     * Check operation status and update prompt accordingly
     * @return true if operation completed, false if still processing
     */
    @Transactional
    public boolean checkAndUpdateOperationStatus(Prompt prompt) {
        if (prompt.getGeminiFileId() == null || prompt.getGeminiFileId().isBlank()) {
            log.warn("Prompt {} has no operation/document ID to poll", prompt.getId());
            return false;
        }

        // Check if already converted to document ID
        if (prompt.getGeminiFileId().contains(DOCUMENT_NAME_FACTOR)) {
            log.debug("Prompt {} already has document ID: {}",
                    prompt.getId(), prompt.getGeminiFileId());

            // Ensure status is correct
            if (!"indexed".equals(prompt.getIndexingStatus())) {
                prompt.setIndexingStatus("indexed");
                prompt.setLastIndexedAt(Instant.now());
                promptRepository.save(prompt);
            }
            return true;
        }

        // Must be an operation ID - poll it
        if (!prompt.getGeminiFileId().contains(OPERATION_NAME_FACTOR)) {
            log.error("Prompt {} has invalid geminiFileId format: {}",
                    prompt.getId(), prompt.getGeminiFileId());
            prompt.setIndexingStatus("failed");
            promptRepository.save(prompt);
            return true;
        }

        log.debug("Polling operation {} for prompt {}",
                prompt.getGeminiFileId(), prompt.getId());

        ImportOperationResponse operationResponse =
                geminiClientService.pollOperation(prompt.getGeminiFileId());

        // Check if operation failed
        if ("failed".equals(operationResponse.status())) {
            log.error("Operation failed for prompt {}: {}",
                    prompt.getId(), operationResponse.errorMessage());
            prompt.setIndexingStatus("failed");
            promptRepository.save(prompt);
            return true;
        }

        // Check if operation completed
        if (operationResponse.done()) {
            String documentId = operationResponse.documentId();

            if (documentId != null && !documentId.isBlank() &&
                    documentId.contains(DOCUMENT_NAME_FACTOR)) {

                log.info("Operation completed for prompt {}. Document ID: {}",
                        prompt.getId(), documentId);

                // Replace operation ID with document ID
                prompt.setGeminiFileId(documentId);
                prompt.setIndexingStatus("indexed");
                prompt.setLastIndexedAt(Instant.now());

                promptRepository.save(prompt);

                log.info("Successfully indexed prompt {} with document {}",
                        prompt.getId(), documentId);

                return true;
            } else {
                log.error("Operation completed but no valid document ID for prompt {}",
                        prompt.getId());
                prompt.setIndexingStatus("failed");
                promptRepository.save(prompt);
                return true;
            }
        } else {
            // Still processing
            log.debug("Operation {} still processing for prompt {}",
                    prompt.getGeminiFileId(), prompt.getId());
            return false;
        }
    }

    /**
     * Manual trigger to poll a specific prompt's operation
     */
    @Transactional
    public boolean pollOperationForPrompt(UUID promptId) {
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found"));

        return checkAndUpdateOperationStatus(prompt);
    }
}