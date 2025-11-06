package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.request.prompt.PromptOptimizationRequest;
import SEP490.EduPrompt.dto.response.prompt.OptimizationQueueResponse;
import SEP490.EduPrompt.enums.AiModel;
import SEP490.EduPrompt.enums.QueueStatus;
import SEP490.EduPrompt.enums.QuotaType;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.OptimizationQueue;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.repo.OptimizationQueueRepository;
import SEP490.EduPrompt.repo.PromptRepository;
import SEP490.EduPrompt.repo.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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
                        .promptId(prompt.getId())
                        .requestedBy(user)
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

                return queueRepository.save(queueEntry);
            });
            //publish event to redis
            publishOptimizationEvent(savedQueue.getId());

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

    @Override
    @Transactional(readOnly = true)
    public Page<OptimizationQueueResponse> getUserOptimizationHistory(UUID userId, Pageable pageable) {
        log.info("Fetching optimization history for user: {}", userId);

        Page<OptimizationQueue> queuePage = queueRepository.findByRequestedByIdOrderByCreatedAtDesc(
                userId,
                pageable
        );

        return queuePage.map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OptimizationQueueResponse> getPromptOptimizationHistory(UUID promptId, UUID userId, Pageable pageable) {
        log.info("Fetching optimization history for prompt: {} by user: {}", promptId, userId);

        Page<OptimizationQueue> queuePage = queueRepository.findByPromptIdAndRequestedByIdOrderByCreatedAtDesc(
                promptId,
                userId,
                pageable
        );

        return queuePage.map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OptimizationQueueResponse> getPendingOptimizations(UUID userId) {
        log.info("Fetching pending optimizations for user: {}", userId);

        List<OptimizationQueue> pendingQueues = queueRepository.findByRequestedByIdAndStatusInOrderByCreatedAtDesc(
                userId,
                List.of(QueueStatus.PENDING.name(), QueueStatus.PROCESSING.name())
        );

        return pendingQueues.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public OptimizationQueueResponse retryOptimization(UUID queueId, UUID userId) {
        log.info("Retrying optimization: {} by user: {}", queueId, userId);

        OptimizationQueue queueEntry = queueRepository.findById(queueId)
                .orElseThrow(() -> new ResourceNotFoundException("Queue not found with id: " + queueId));

        // check owner
        if (!queueEntry.getRequestedById().equals(userId)) {
            log.warn("User {} attempted to retry queue {} owned by {}",
                    userId, queueId, queueEntry.getRequestedById());
            throw new ResourceNotFoundException("Queue not found with id: " + queueId);
        }

        // check if queue status is failed
        if (!QueueStatus.FAILED.name().equals(queueEntry.getStatus())) {
            throw new InvalidInputException("Cannot retry optimization with status: " + queueEntry.getStatus());
        }

        // reset for retry
        queueEntry.setStatus(QueueStatus.PENDING.name());
        queueEntry.setRetryCount(0);
        queueEntry.setErrorMessage(null);
        queueEntry.setUpdatedAt(Instant.now());

        OptimizationQueue savedQueue = queueRepository.save(queueEntry);
        log.info("Optimization queued for retry: {}", queueId);

        return mapToResponse(savedQueue);
    }

    @Override
    @Transactional
    public void cancelOptimization(UUID queueId, UUID userId) {
        log.info("Cancelling optimization: {} by user: {}", queueId, userId);

        OptimizationQueue queueEntry = queueRepository.findById(queueId)
                .orElseThrow(() -> new ResourceNotFoundException("Queue not found with id: " + queueId));

        // check owner
        if (!queueEntry.getRequestedById().equals(userId)) {
            log.warn("User {} attempted to cancel queue {} owned by {}",
                    userId, queueId, queueEntry.getRequestedById());
            throw new ResourceNotFoundException("Queue not found with id: " + queueId);
        }

        // can only cancel pending or failed
        String status = queueEntry.getStatus();
        if (!QueueStatus.PENDING.name().equals(status) && !QueueStatus.FAILED.name().equals(status)) {
            throw new InvalidInputException("Cannot cancel optimization with status: " + status);
        }

        queueRepository.delete(queueEntry);
        log.info("Optimization cancelled successfully: {}", queueId);
    }


    /**
     * Publish optimization event to Redis
     */
    private void publishOptimizationEvent(UUID queueId) {
        try {
            redisTemplate.convertAndSend(
                    "queue:optimization",
                    queueId.toString()
            );
            log.debug("Published optimization event for queue: {}", queueId);
        } catch (Exception e) {
            log.error("Failed to publish optimization event, will rely on fallback scheduler", e);
        }
    }

    /**
     * fallback: process queue in batches if events fail
     * Runs every 10 minutes as backup only
     */
    @Override
    @Scheduled(fixedDelay = 600000, initialDelay = 60000) // Every 10 minutes
    public void processOptimizationQueue() {
        log.debug("Running fallback queue processor");

        // Quick check - exit early if no pending items
        long pendingCount = queueRepository.countByStatus(QueueStatus.PENDING.name());
        if (pendingCount == 0) {
            return;
        }

        log.warn("Found {} pending optimizations, processing (event system may have failed)", pendingCount);

        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        List<OptimizationQueue> pendingItems = queueRepository.findPendingItemsForProcessing(
                QueueStatus.PENDING.name(), pageable
        ).getContent();

        // Re-trigger events for pending items
        for (OptimizationQueue item : pendingItems) {
            publishOptimizationEvent(item.getId());
        }
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