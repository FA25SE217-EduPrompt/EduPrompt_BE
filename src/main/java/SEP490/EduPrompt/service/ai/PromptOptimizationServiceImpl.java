package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.request.prompt.PromptOptimizationRequest;
import SEP490.EduPrompt.dto.response.prompt.ClientPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.OptimizationQueueResponse;
import SEP490.EduPrompt.enums.AiModel;
import SEP490.EduPrompt.enums.QueueStatus;
import SEP490.EduPrompt.enums.QuotaType;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.AiSuggestionLog;
import SEP490.EduPrompt.model.OptimizationQueue;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.repo.AiSuggestionLogRepository;
import SEP490.EduPrompt.repo.OptimizationQueueRepository;
import SEP490.EduPrompt.repo.PromptRepository;
import SEP490.EduPrompt.repo.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Service
@Slf4j
@RequiredArgsConstructor
public class PromptOptimizationServiceImpl implements PromptOptimizationService {

    protected static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:optimization:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final int BATCH_SIZE = 10;
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(30);

    private final QuotaService quotaService;
    private final PromptRepository promptRepository;
    private final OptimizationQueueRepository queueRepository;
    private final AiSuggestionLogRepository suggestionRepository;
    private final AiClientService aiClientService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;

    @Override
    public OptimizationQueueResponse requestOptimization(UUID userId, PromptOptimizationRequest request,
                                                         String idempotencyKey) {
        log.info("Requesting optimization for prompt: {} by user: {} with idempotency key: {}",
                request.promptId(), userId, idempotencyKey);

        String cacheKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;

        // Check cache BEFORE acquiring lock (fast path)
        OptimizationQueueResponse cachedResponse = getCachedResponse(cacheKey);
        if (cachedResponse != null) {
            log.info("Returning cached result for idempotency key: {}", idempotencyKey);
            return cachedResponse;
        }

        // Acquire distributed lock
        String lockKey = "lock:" + cacheKey;
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                userId.toString(),
                LOCK_TIMEOUT
        );

        if (!lockAcquired) {
            log.warn("Concurrent request detected for idempotency key: {}, rejecting", idempotencyKey);
            throw new InvalidInputException("Duplicate request in progress, please retry in a moment");
        }

        try {
            // Double-check cache after acquiring lock
            cachedResponse = getCachedResponse(cacheKey);
            if (cachedResponse != null) {
                log.info("Result appeared while waiting for lock, returning cached result");
                return cachedResponse;
            }

            // Check database for existing request (outside transaction)
            OptimizationQueue existingQueue = queueRepository.findByIdempotencyKey(idempotencyKey)
                    .orElse(null);

            if (existingQueue != null) {
                log.info("Found existing queue entry in DB for idempotency key: {}", idempotencyKey);
                OptimizationQueueResponse response = mapToResponse(existingQueue);
                cacheIdempotencyResult(cacheKey, response);
                return response;
            }

            // Validate quota BEFORE starting transaction
            quotaService.validateQuota(userId, QuotaType.OPTIMIZATION, request.maxTokens());

            // Verify prompt exists BEFORE starting transaction
            Prompt prompt = promptRepository.findById(request.promptId())
                    .orElseThrow(() -> new ResourceNotFoundException("prompt not found with id: " + request.promptId()));

            // Use TransactionTemplate for explicit transaction control
            OptimizationQueue savedQueue = transactionTemplate.execute(status -> {
                User user = userRepository.getReferenceById(userId);

                OptimizationQueue queueEntry = OptimizationQueue.builder()
                        .prompt(prompt)
                        .requestedBy(user)
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

                return queueRepository.save(queueEntry);
            });

            // Cache result AFTER transaction commits successfully
            OptimizationQueueResponse response = mapToResponse(savedQueue);
            cacheIdempotencyResult(cacheKey, response);

            log.info("Optimization request queued. Queue ID: {}, Status: {}",
                    savedQueue.getId(), savedQueue.getStatus());

            return response;

        } finally {
            // Always release lock
            redisTemplate.delete(lockKey);
            log.debug("Released distributed lock for key: {}", idempotencyKey);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OptimizationQueueResponse getOptimizationStatus(UUID queueId, UUID userId) {
        log.info("Fetching optimization status for queue: {} by user: {}", queueId, userId);

        OptimizationQueue queueEntry = queueRepository.findById(queueId)
                .orElseThrow(() -> new ResourceNotFoundException("queue not found with id: " + queueId));

        // Verify user owns this optimization request
        if (!queueEntry.getRequestedById().equals(userId)) {
            log.warn("User {} attempted to access queue {} owned by {}",
                    userId, queueId, queueEntry.getRequestedById());
            throw new ResourceNotFoundException("queue not found with id: " + queueId);
        }

        return mapToResponse(queueEntry);
    }

    /**
     * Process queue in batches , no @Transaction
     * Each item gets its own transaction via TransactionTemplate
     */
    @Override
    @Scheduled(fixedDelay = 30000)
    public void processOptimizationQueue() {
        log.info("Starting optimization queue processing");

        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        List<OptimizationQueue> pendingItems;

        do {
            // Fetch batch (outside transaction)
            pendingItems = queueRepository.findPendingItemsForProcessing(
                    QueueStatus.PENDING.name(), pageable
            ).getContent();

            if (pendingItems.isEmpty()) {
                break;
            }

            log.info("Processing batch of {} pending optimization requests", pendingItems.size());

            // Process each item in its own transaction using TransactionTemplate
            for (OptimizationQueue item : pendingItems) {
                processOptimizationItemWithTransaction(item);
            }

        } while (pendingItems.size() == BATCH_SIZE);

        log.info("Optimization queue processing completed");
    }

    /**
     * Using TransactionTemplate
     * Each item processed in its own transaction
     */
    protected void processOptimizationItemWithTransaction(OptimizationQueue item) {
        try {
            // Execute in a NEW transaction using TransactionTemplate
            transactionTemplate.execute(status -> {
                try {
                    // Reload item to ensure we have latest version (optimistic locking)
                    OptimizationQueue currentItem = queueRepository.findById(item.getId())
                            .orElseThrow(() -> new ResourceNotFoundException("Queue item not found: " + item.getId()));

                    // Check if already being processed or completed
                    if (!QueueStatus.PENDING.name().equals(currentItem.getStatus())) {
                        log.info("Item {} already processed, skipping. Status: {}",
                                currentItem.getId(), currentItem.getStatus());
                        return null;
                    }

                    // Process the item
                    processOptimizationItem(currentItem);
                    return null;

                } catch (Exception e) {
                    log.error("Failed to process optimization item: {}, handling failure", item.getId(), e);
                    handleOptimizationFailure(item, e.getMessage());
                    // Don't re-throw - we want to commit the failure status
                    return null;
                }
            });

        } catch (Exception e) {
            log.error("Error in transaction execution for item: {}", item.getId(), e);
        }
    }

    /**
     * Core processing logic - runs within transaction from TransactionTemplate
     */
    private void processOptimizationItem(OptimizationQueue item) {
        log.info("Processing optimization item: {}", item.getId());

        // Update status to PROCESSING
        item.setStatus(QueueStatus.PROCESSING.name());
        item.setUpdatedAt(Instant.now());
        queueRepository.saveAndFlush(item); // flush to prevent duplicate processing

        UUID userId = item.getRequestedById();
        int reservedTokens = item.getMaxTokens();

        try {
            // Reserve tokens (should use MANDATORY propagation to join this transaction)
            quotaService.validateAndDecrementQuota(userId, QuotaType.OPTIMIZATION, reservedTokens);

            Prompt prompt = promptRepository.findById(item.getPromptId())
                    .orElseThrow(() -> new ResourceNotFoundException("prompt not found with id: " + item.getPromptId()));

            // Call AI model - this is external call, not in transaction
            ClientPromptResponse optimizedPrompt = aiClientService.optimizePrompt(
                    prompt,
                    item.getInput(),
                    item.getTemperature(),
                    item.getMaxTokens()
            );

            int tokensUsed = optimizedPrompt.totalTokens();

            // refund unused tokens
            int tokensToRefund = reservedTokens - tokensUsed;
            if (tokensToRefund > 0) {
                quotaService.refundTokens(userId, tokensToRefund);
                log.debug("Refunded {} unused tokens for user: {}", tokensToRefund, userId);
            }

            // Save suggestion log
            AiSuggestionLog suggestionLog = AiSuggestionLog.builder()
                    .prompt(prompt)
                    .promptId(prompt.getId())
                    .requestedBy(userId)
                    .input(item.getInput())
                    .output(optimizedPrompt.content())
                    .aiModel(item.getAiModel())
                    .status(QueueStatus.COMPLETED.name())
                    .optimizationQueue(item)
                    .createdAt(Instant.now())
                    .build();

            suggestionRepository.save(suggestionLog);

            // Update queue status
            item.setOutput(optimizedPrompt.content());
            item.setStatus(QueueStatus.COMPLETED.name());
            item.setUpdatedAt(Instant.now());
            queueRepository.save(item);

            log.info("Optimization completed successfully for item: {}", item.getId());

        } catch (Exception e) {
            // Refund all reserved tokens on failure
            log.error("AI call failed for optimization item: {}, refunding quota", item.getId(), e);
            quotaService.refundQuota(userId, QuotaType.OPTIMIZATION, reservedTokens);
            throw e; // Re-throw to trigger rollback
        }
    }

    /**
     * Handle failure - run in same transaction as this method processOptimizationItemWithTransaction()
     */
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

    /**
     * Helper: Get cached response with error handling
     */
    private OptimizationQueueResponse getCachedResponse(String cacheKey) {
        try {
            String cachedResult = redisTemplate.opsForValue().get(cacheKey);
            if (cachedResult != null) {
                return objectMapper.readValue(cachedResult, OptimizationQueueResponse.class);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize cached result for key: {}", cacheKey, e);
            // Delete corrupted cache entry
            redisTemplate.delete(cacheKey);
        }
        return null;
    }

    /**
     * Helper: Cache result with error handling
     */
    private void cacheIdempotencyResult(String cacheKey, OptimizationQueueResponse response) {
        try {
            String jsonResult = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, jsonResult, IDEMPOTENCY_TTL);
            log.debug("Cached idempotency result with TTL: {}", IDEMPOTENCY_TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to cache idempotency result for key: {}", cacheKey, e);
            // Not fatal, continue
        }
    }

    /**
     * Helper: mapper (i dont like mapper tho :<)
     */
    private OptimizationQueueResponse mapToResponse(OptimizationQueue queue) {
        return OptimizationQueueResponse.builder()
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