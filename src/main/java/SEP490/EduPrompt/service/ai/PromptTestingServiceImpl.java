package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.request.prompt.PromptTestRequest;
import SEP490.EduPrompt.dto.response.prompt.ClientPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.PromptTestResponse;
import SEP490.EduPrompt.enums.AiModel;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PromptTestingServiceImpl implements PromptTestingService {

    protected static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:test:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final QuotaService quotaService;
    private final PromptRepository promptRepository;
    private final PromptUsageRepository usageRepository;
    private final AiClientService aiClientService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    @Override
    public PromptTestResponse testPrompt(UUID userId, PromptTestRequest request, String idempotencyKey) {
        log.info("Testing prompt: {} for user: {} with idempotency key: {}",
                request.promptId(), userId, idempotencyKey);

        // check redis cache for idempotency
        String cacheKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        String cachedResult = redisTemplate.opsForValue().get(cacheKey);

        if (cachedResult != null) { //no this could be null, dont trust the ide
            try {
                log.info("Idempotent retry detected in Redis for key: {}, returning cached result", idempotencyKey);
                return objectMapper.readValue(cachedResult, PromptTestResponse.class);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize cached result, will reprocess", e);
                // not fatal, continue
            }
        }
        // check database as fallback if not found in redis
        Optional<PromptUsage> existingUsage = usageRepository.findByIdempotencyKey(idempotencyKey);
        if (existingUsage.isPresent()) {
            log.info("Idempotent retry detected in DB for key: {}, caching and returning result", existingUsage.get().getIdempotencyKey());
            PromptTestResponse response = mapToResponse(existingUsage.get());
            cacheIdempotencyResult(cacheKey, response);
            return response;
        }

        // lock to prevent concurrent processing of same idempotency key
        String lockKey = "lock:" + cacheKey;
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                userId.toString(),
                Duration.ofSeconds(60) // Lock timeout
        );

        if (!lockAcquired) {
            log.warn("Concurrent request detected for idempotency key: {}, rejecting", idempotencyKey);
            throw new InvalidInputException("Duplicate request in progress, please retry in a moment");
        }

        try {
            // double-check after acquiring lock
            cachedResult = redisTemplate.opsForValue().get(cacheKey);
            if (cachedResult != null) { //no this could be null, dont trust the ide
                try {
                    log.info("Idempotent retry detected in Redis for key: {}, returning cached result", idempotencyKey);
                    return objectMapper.readValue(cachedResult, PromptTestResponse.class);
                } catch (JsonProcessingException e) {
                    log.error("Failed to deserialize cached result, will reprocess", e);
                    // not fatal, continue
                }
            }

            //  assume decrement token used by max token in user request
            int reservedTokens = request.maxTokens();
            quotaService.validateAndDecrementQuota(userId, QuotaType.TEST, reservedTokens);

            Prompt prompt = promptRepository.findById(request.promptId())
                    .orElseThrow(() -> new ResourceNotFoundException("prompt not found with id: " + request.promptId()));

            // calling
            long startTime = System.currentTimeMillis();
            ClientPromptResponse response;
            PromptUsage savedUsage;

            try {
                response = aiClientService.testPrompt(
                        prompt,
                        request.aiModel(),
                        request.inputText(),
                        request.temperature(),
                        request.maxTokens(),
                        request.topP()
                );

                int executionTime = (int) (System.currentTimeMillis() - startTime);
                int tokensUsed = response.totalTokens();

                // refund if assuming mismatch
                int tokensToRefund = reservedTokens - tokensUsed;
                if (tokensToRefund > 0) {
                    quotaService.refundTokens(userId, tokensToRefund);
                    log.debug("Refunded {} unused tokens for user: {}", tokensToRefund, userId);
                }
                User user = userRepository.getReferenceById(userId);

                PromptUsage usage = PromptUsage.builder()
                        .prompt(prompt)
                        .promptId(prompt.getId())
                        .user(user)
                        .aiModel(request.aiModel().getName())
                        .inputText(request.inputText())
                        .output(response.content())
                        .tokensUsed(tokensUsed)
                        .executionTimeMs(executionTime)
                        .idempotencyKey(idempotencyKey)
                        .temperature(request.temperature())
                        .maxTokens(request.maxTokens())
                        .topP(request.topP())
                        .createdAt(Instant.now())
                        .build();

                savedUsage = usageRepository.save(usage);
                log.info("Prompt test completed. Usage ID: {}, Execution time: {}ms",
                        savedUsage.getId(), executionTime);

            } catch (Exception e) {
                //refund everything on failure, i dont like this
                log.error("AI call failed for user: {}, refunding quota", userId, e);
                quotaService.refundQuota(userId, QuotaType.TEST, reservedTokens);
                throw new AiProviderException("Failed to test prompt: " + e.getMessage());
            }

            // cache the result
            PromptTestResponse testResponse = mapToResponse(savedUsage);
            cacheIdempotencyResult(cacheKey, testResponse);

            return testResponse;

        } finally {
            // release lock
            redisTemplate.delete(lockKey);
            log.debug("Released distributed lock for key: {}", idempotencyKey);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PromptTestResponse getTestResult(UUID usageId) {
        log.info("Fetching test result: {}", usageId);

        PromptUsage usage = usageRepository.findById(usageId)
                .orElseThrow(() -> new RuntimeException("Prompt usage not found: " + usageId));

        return mapToResponse(usage);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PromptTestResponse> getUserTestHistory(UUID userId, Pageable pageable) {
        log.info("Fetching test history for user: {}", userId);

        Page<PromptUsage> usagePage = usageRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return usagePage.map(this::mapToResponse);
    }

    private PromptTestResponse mapToResponse(PromptUsage usage) {
        return  PromptTestResponse.builder()
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
                .createdAt(usage.getCreatedAt())
                .build();
    }

    private void cacheIdempotencyResult(String cacheKey, PromptTestResponse response) {
        try {
            String jsonResult = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, jsonResult, IDEMPOTENCY_TTL);
            log.debug("Cached idempotency result with TTL: {}", IDEMPOTENCY_TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to cache idempotency result", e);
            // not fatal, continue
        }
    }
}