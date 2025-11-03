package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.request.prompt.PromptTestRequest;
import SEP490.EduPrompt.dto.response.prompt.ClientPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.PromptTestResponse;
import SEP490.EduPrompt.enums.AiModel;
import SEP490.EduPrompt.enums.QueueStatus;
import SEP490.EduPrompt.enums.QuotaType;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.exception.client.AiProviderException;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.model.PromptUsage;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.repo.PromptRepository;
import SEP490.EduPrompt.repo.PromptUsageRepository;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Event-driven PromptTestingService
 * - Publishes events to Redis for immediate processing
 * - Fallback scheduler
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PromptTestingServiceImpl implements PromptTestingService {

    protected static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:test:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(30);
    private static final int AI_CALL_TIMEOUT_SECONDS = 30;

    private final QuotaService quotaService;
    private final PromptRepository promptRepository;
    private final PromptUsageRepository usageRepository;
    private final AiClientService aiClientService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;
    private final PromptUsageService promptUsageService;

    /**
     * Main test method - decides between sync and async
     */
    @Override
    public PromptTestResponse testPrompt(UUID userId, PromptTestRequest request, String idempotencyKey) {
        // use async for heavy workloads
        if (shouldUseAsync(request)) {
            log.info("Using async processing for request: {}", request.promptId());
            return testPromptAsync(userId, request, idempotencyKey);
        } else {
            log.info("Using sync processing for request: {}", request.promptId());
            return testPromptSync(userId, request, idempotencyKey);
        }
    }

    /**
     * Decision logic: when to use async
     */
    private boolean shouldUseAsync(PromptTestRequest request) {
        // Use async if high token count or long input
        return request.maxTokens() > 1000 ||
                (request.inputText() != null && request.inputText().length() > 2000);
    }

    /**
     * Synchronous test (for quick responses)
     */
    private PromptTestResponse testPromptSync(UUID userId, PromptTestRequest request, String idempotencyKey) {
        log.info("Testing prompt synchronously: {} for user: {}", request.promptId(), userId);

        String cacheKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;

        // Check cache first
        PromptTestResponse cachedResponse = getCachedResponse(cacheKey);
        if (cachedResponse != null) {
            log.info("Returning cached result");
            return cachedResponse;
        }

        // Acquire lock
        String lockKey = "lock:" + cacheKey;
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(
                lockKey, userId.toString(), LOCK_TIMEOUT
        );

        if (Boolean.FALSE.equals(lockAcquired)) {
            throw new InvalidInputException("Duplicate request in progress");
        }

        try {
            // Double-check cache
            cachedResponse = getCachedResponse(cacheKey);
            if (cachedResponse != null) {
                return cachedResponse;
            }

            // Check DB
            PromptUsage existingUsage = usageRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (existingUsage != null) {
                PromptTestResponse response = mapToResponse(existingUsage);
                cacheIdempotencyResult(cacheKey, response);
                return response;
            }

            // Reserve quota
            int reservedTokens = request.maxTokens();
            quotaService.validateAndDecrementQuota(userId, QuotaType.TEST, reservedTokens);

            // Fetch prompt
            Prompt prompt = promptRepository.findById(request.promptId())
                    .orElseThrow(() -> new ResourceNotFoundException("prompt not found"));

            // Call AI with timeout
            long startTime = System.currentTimeMillis();
            ClientPromptResponse aiResponse;

            try {
                aiResponse = callAiWithTimeout(prompt, request);
            } catch (Exception e) {
                log.error("AI call failed, refunding tokens", e);
                quotaService.refundQuota(userId, QuotaType.TEST, reservedTokens);
                throw new AiProviderException("Failed to test prompt: " + e.getMessage());
            }

            int executionTime = (int) (System.currentTimeMillis() - startTime);
            int tokensUsed = aiResponse.totalTokens();

            // Refund unused
            int tokensToRefund = reservedTokens - tokensUsed;
            if (tokensToRefund > 0) {
                quotaService.refundTokens(userId, tokensToRefund);
            }

            // Save in transaction
            PromptUsage savedUsage = promptUsageService.saveUsage(
                    userId, prompt, request, aiResponse,
                    tokensUsed, executionTime, idempotencyKey
            );

            // Cache result
            PromptTestResponse response = mapToResponse(savedUsage);
            cacheIdempotencyResult(cacheKey, response);

            log.info("Sync test completed: {}", savedUsage.getId());
            return response;

        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * Async test (for long-running requests)
     */
    private PromptTestResponse testPromptAsync(UUID userId, PromptTestRequest request, String idempotencyKey) {
        log.info("Queueing async prompt test: {}", request.promptId());

        String cacheKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;

        // Check cache
        PromptTestResponse cachedResponse = getCachedResponse(cacheKey);
        if (cachedResponse != null) {
            return cachedResponse;
        }

        // Acquire lock
        String lockKey = "lock:" + cacheKey;
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(
                lockKey, userId.toString(), LOCK_TIMEOUT
        );

        if (!lockAcquired) {
            throw new InvalidInputException("Duplicate request in progress");
        }

        try {
            // Check DB
            PromptUsage existingUsage = usageRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (existingUsage != null) {
                return mapToResponse(existingUsage);
            }

            // Validate quota (don't decrement yet)
            quotaService.validateQuota(userId, QuotaType.TEST, request.maxTokens());

            // Create pending entry
            PromptUsage pendingUsage = transactionTemplate.execute(status -> {
                Prompt prompt = promptRepository.findById(request.promptId())
                        .orElseThrow(() -> new ResourceNotFoundException("prompt not found"));

                User user = userRepository.getReferenceById(userId);

                PromptUsage usage = PromptUsage.builder()
                        .prompt(prompt)
                        .promptId(prompt.getId())
                        .user(user)
                        .userId(userId)
                        .aiModel(request.aiModel().getName())
                        .inputText(request.inputText())
                        .status(QueueStatus.PENDING.name())
                        .idempotencyKey(idempotencyKey)
                        .temperature(request.temperature())
                        .maxTokens(request.maxTokens())
                        .topP(request.topP())
                        .createdAt(Instant.now())
                        .build();

                return usageRepository.save(usage);
            });

            // Publish event for immediate processing
            publishTestEvent(pendingUsage.getId());

            // Return pending response
            return mapToResponse(pendingUsage);

        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * Publish test event to Redis
     */
    private void publishTestEvent(UUID usageId) {
        try {
            redisTemplate.convertAndSend("queue:test", usageId.toString());
            log.debug("Published test event for usage: {}", usageId);
        } catch (Exception e) {
            log.error("Failed to publish test event, will rely on fallback scheduler", e);
        }
    }

    /**
     * FALLBACK: Process pending tests if events fail
     * Runs every 5 minutes as backup
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    public void processPendingTests() {
        log.debug("Running fallback test processor");

        // Quick check
        long pendingCount = usageRepository.countByStatus(QueueStatus.PENDING.name());
        if (pendingCount == 0) {
            return;
        }

        log.warn("Found {} pending tests, re-triggering events", pendingCount);

        List<PromptUsage> pendingTests = usageRepository
                .findByStatusOrderByCreatedAtAsc(QueueStatus.PENDING.name(), PageRequest.of(0, 10))
                .getContent();

        // Re-trigger events
        for (PromptUsage usage : pendingTests) {
            publishTestEvent(usage.getId());
        }
    }

    /**
     * Call AI with timeout
     */
    private ClientPromptResponse callAiWithTimeout(Prompt prompt, PromptTestRequest request) {
        try {
            CompletableFuture<ClientPromptResponse> future = CompletableFuture.supplyAsync(() ->
                    aiClientService.testPrompt(
                            prompt,
                            request.aiModel(),
                            request.inputText(),
                            request.temperature(),
                            request.maxTokens(),
                            request.topP()
                    )
            );

            return future.get(AI_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        } catch (TimeoutException e) {
            throw new AiProviderException("AI request timed out after " + AI_CALL_TIMEOUT_SECONDS + " seconds");
        } catch (Exception e) {
            throw new AiProviderException("AI call failed: " + e.getMessage());
        }
    }

    /**
     * Save usage in transaction
     * deprecated, using promptUsageService instead
     */
    @Deprecated
    @Transactional
    protected PromptUsage saveUsageInTransaction(
            UUID userId, Prompt prompt, PromptTestRequest request,
            ClientPromptResponse aiResponse, int tokensUsed,
            int executionTime, String idempotencyKey) {

        User user = userRepository.getReferenceById(userId);

        PromptUsage usage = PromptUsage.builder()
                .prompt(prompt)
                .promptId(prompt.getId())
                .user(user)
                .aiModel(request.aiModel().getName())
                .inputText(request.inputText())
                .output(aiResponse.content())
                .tokensUsed(tokensUsed)
                .executionTimeMs(executionTime)
                .idempotencyKey(idempotencyKey)
                .temperature(request.temperature())
                .maxTokens(request.maxTokens())
                .topP(request.topP())
                .status(QueueStatus.COMPLETED.name())
                .createdAt(Instant.now())
                .build();

        return usageRepository.save(usage);
    }

    @Override
    @Transactional(readOnly = true)
    public PromptTestResponse getTestResult(UUID usageId) {
        PromptUsage usage = usageRepository.findById(usageId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt usage not found"));
        return mapToResponse(usage);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromptTestResponse> getTestResultsByPromptId(UUID promptId) {
        List<PromptUsage> usageList = usageRepository.findByPromptIdOrderByCreatedAtDesc(promptId);
        return usageList.stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PromptTestResponse> getUserTestHistory(UUID userId, Pageable pageable) {
        Page<PromptUsage> usagePage = usageRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return usagePage.map(this::mapToResponse);
    }

    private PromptTestResponse getCachedResponse(String cacheKey) {
        try {
            String cachedResult = redisTemplate.opsForValue().get(cacheKey);
            if (cachedResult != null) { //it can be null, dont trust the ide
                return objectMapper.readValue(cachedResult, PromptTestResponse.class);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize cached result", e);
            redisTemplate.delete(cacheKey);
        }
        return null;
    }

    private void cacheIdempotencyResult(String cacheKey, PromptTestResponse response) {
        try {
            String jsonResult = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, jsonResult, IDEMPOTENCY_TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to cache result", e);
        }
    }

    private PromptTestResponse mapToResponse(PromptUsage usage) {
        return PromptTestResponse.builder()
                .id(usage.getId())
                .promptId(usage.getPromptId())
                .aiModel(AiModel.parseAiModel(usage.getAiModel()))
                .inputText(usage.getInputText())
                .output(usage.getOutput())
                .tokensUsed(usage.getTokensUsed())
                .executionTimeMs(usage.getExecutionTimeMs())
                .temperature(usage.getTemperature())
                .maxTokens(usage.getMaxTokens())
                .topP(usage.getTopP())
                .status(usage.getStatus() != null ? QueueStatus.parseQueueStatus(usage.getStatus()) : null)
                .createdAt(usage.getCreatedAt())
                .build();
    }
}