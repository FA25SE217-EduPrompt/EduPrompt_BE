package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.request.prompt.PromptOptimizationRequest;
import SEP490.EduPrompt.dto.response.prompt.ClientPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.OptimizationQueueResponse;
import SEP490.EduPrompt.enums.AiModel;
import SEP490.EduPrompt.enums.QueueStatus;
import SEP490.EduPrompt.enums.QuotaType;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.AiSuggestionLog;
import SEP490.EduPrompt.model.OptimizationQueue;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.repo.AiSuggestionLogRepository;
import SEP490.EduPrompt.repo.OptimizationQueueRepository;
import SEP490.EduPrompt.repo.PromptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PromptOptimizationServiceImpl implements PromptOptimizationService {

    private final QuotaService quotaService;
    private final PromptRepository promptRepository;
    private final OptimizationQueueRepository queueRepository;
    private final AiSuggestionLogRepository suggestionRepository;
    private final AiClientService aiClientService;

    @Override
    @Transactional
    public OptimizationQueueResponse requestOptimization(UUID userId, PromptOptimizationRequest request,
                                                         String idempotencyKey) {
        log.info("Requesting optimization for prompt: {} by user: {} with idempotency key: {}",
                request.promptId(), userId, idempotencyKey);

        // Check idempotency - if already processed, return cached result
        Optional<OptimizationQueue> existingQueue = queueRepository.findByIdempotencyKey(idempotencyKey);
        if (existingQueue.isPresent()) {
            log.info("Idempotent retry detected for key: {}, returning cached result", idempotencyKey);
            return mapToResponse(existingQueue.get());
        }

        // validate quota first
        quotaService.validateQuota(userId, QuotaType.OPTIMIZATION, request.maxTokens());

        // Verify prompt exists
        promptRepository.findById(request.promptId())
                .orElseThrow(() -> new ResourceNotFoundException("prompt not found with id" + request.promptId()));

        // Create queue entry
        OptimizationQueue queueEntry = OptimizationQueue.builder()
                .promptId(request.promptId())
                .requestedById(userId)
                .input(request.optimizationInput())
                .status(QueueStatus.PENDING.name())
                .aiModel(AiModel.GPT_4O_MINI.getName())
                .idempotencyKey(idempotencyKey)
                .retryCount(0)
                .maxRetries(3)
                .temperature(request.temperature())
                .maxTokens(request.maxTokens())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        OptimizationQueue savedQueue = queueRepository.save(queueEntry);

        log.info("Optimization request queued. Queue ID: {}, Status: {}",
                savedQueue.getId(), savedQueue.getStatus());

        return mapToResponse(savedQueue);
    }

    @Override
    @Transactional(readOnly = true)
    public OptimizationQueueResponse getOptimizationStatus(UUID queueId, UUID userId) {
        log.info("Fetching optimization status for queue: {} by user: {}", queueId, userId);

        OptimizationQueue queueEntry = queueRepository.findById(queueId)
                .orElseThrow(() -> new ResourceNotFoundException("queue not found with id" + queueId));

        // Verify user owns this optimization request
        if (!queueEntry.getRequestedById().equals(userId)) {
            log.warn("User {} attempted to access queue {} owned by {}",
                    userId, queueId, queueEntry.getRequestedById());
            throw new ResourceNotFoundException("queue not found with id" + queueId);
        }

        return mapToResponse(queueEntry);
    }

    @Override
    @Scheduled(fixedDelay = 30000) // Run every 30 seconds
    @Async
    @Transactional
    public void processOptimizationQueue() {
        log.info("Starting optimization queue processing");

        List<OptimizationQueue> pendingItems = queueRepository
                .findPendingItemsForProcessing(QueueStatus.PENDING);

        if (pendingItems.isEmpty()) {
            log.debug("No pending optimization requests to process");
            return;
        }

        log.info("Processing {} pending optimization requests", pendingItems.size());

        for (OptimizationQueue item : pendingItems) {
            try {
                processOptimizationItem(item);
            } catch (Exception e) {
                log.error("Error processing optimization queue item: {}", item.getId(), e);
                handleOptimizationFailure(item, e.getMessage());
            }
        }

        log.info("Optimization queue processing completed");
    }

    private void processOptimizationItem(OptimizationQueue item) {
        log.info("Processing optimization item: {}", item.getId());

        // Update status to PROCESSING
        item.setStatus(QueueStatus.PROCESSING.name());
        item.setUpdatedAt(Instant.now());
        queueRepository.save(item);

        // Fetch prompt
        Prompt prompt = promptRepository.findById(item.getPromptId())
                .orElseThrow(() -> new ResourceNotFoundException("prompt not found with id: " + item.getPromptId()));

        // Call AI model for optimization
        ClientPromptResponse optimizedPrompt = aiClientService.optimizePrompt(
                prompt,
                item.getInput(),
                item.getTemperature(),
                item.getMaxTokens()
        );

        // decrement quota & token
        quotaService.decrementQuota(item.getRequestedById(), QuotaType.OPTIMIZATION, optimizedPrompt.totalTokens());

        // Save suggestion log
        AiSuggestionLog suggestionLog = AiSuggestionLog.builder()
                .promptId(item.getPromptId())
                .requestedBy(item.getRequestedById())
                .input(item.getInput())
                .output(optimizedPrompt.content())
                .aiModel(item.getAiModel())
                .status(QueueStatus.COMPLETED.name())
                .optimizationQueueId(item.getId())
                .createdAt(Instant.now())
                .build();

        suggestionRepository.save(suggestionLog);

        // Update queue status to COMPLETED
        item.setOutput(optimizedPrompt.content());
        item.setStatus(QueueStatus.COMPLETED.name());
        item.setUpdatedAt(Instant.now());
        queueRepository.save(item);

        log.info("Optimization completed successfully for item: {}", item.getId());
    }

    private void handleOptimizationFailure(OptimizationQueue item, String errorMessage) {
        item.setRetryCount(item.getRetryCount() + 1);
        item.setErrorMessage(errorMessage);
        item.setUpdatedAt(Instant.now());

        if (item.getRetryCount() >= item.getMaxRetries()) {
            log.warn("Max retries reached for optimization item: {}. Marking as FAILED", item.getId());
            item.setStatus(QueueStatus.FAILED.name());
        } else {
            log.info("Retry {}/{} for optimization item: {}",
                    item.getRetryCount(), item.getMaxRetries(), item.getId());
            item.setStatus(QueueStatus.PENDING.name());
        }

        queueRepository.save(item);
    }

    private OptimizationQueueResponse mapToResponse(OptimizationQueue queue) {
        return  OptimizationQueueResponse.builder()
                .id(queue.getId())
                .promptId(queue.getPromptId())
                .status(QueueStatus.parseQueueStatus(queue.getStatus()))
                .output(queue.getOutput())
                .errorMessage(queue.getErrorMessage())
                .retryCount(queue.getRetryCount())
                .maxRetries(queue.getMaxRetries())
                .createdAt(queue.getCreatedAt())
                .updatedAt(queue.getUpdatedAt())
                .build();

    }
}
