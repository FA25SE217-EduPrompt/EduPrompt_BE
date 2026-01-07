package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.request.prompt.OptimizationRequest;
import SEP490.EduPrompt.dto.request.prompt.PromptOptimizationRequest;
import SEP490.EduPrompt.dto.response.curriculum.CurriculumContext;
import SEP490.EduPrompt.dto.response.curriculum.CurriculumContextDetail;
import SEP490.EduPrompt.dto.response.curriculum.DimensionScore;
import SEP490.EduPrompt.dto.response.curriculum.LessonSuggestion;
import SEP490.EduPrompt.dto.response.prompt.OptimizationQueueResponse;
import SEP490.EduPrompt.dto.response.prompt.OptimizationResponse;
import SEP490.EduPrompt.dto.response.prompt.PromptScoreResult;
import SEP490.EduPrompt.enums.AiModel;
import SEP490.EduPrompt.enums.QueueStatus;
import SEP490.EduPrompt.enums.QuotaType;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.exception.generic.InvalidActionException;
import SEP490.EduPrompt.model.*;
import SEP490.EduPrompt.repo.OptimizationQueueRepository;
import SEP490.EduPrompt.repo.PromptRepository;
import SEP490.EduPrompt.repo.PromptScoreRepository;
import SEP490.EduPrompt.repo.UserRepository;
import SEP490.EduPrompt.service.curriculum.CurriculumMatchingService;
import SEP490.EduPrompt.service.prompt.PromptScoringService;
import SEP490.EduPrompt.service.prompt.PromptVersionService;
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
import java.util.*;
import java.security.MessageDigest;

@Service
@Slf4j
@RequiredArgsConstructor
public class PromptOptimizationServiceImpl implements PromptOptimizationService {

    protected static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:optimization:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final int BATCH_SIZE = 10;
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(30);

    private final QuotaService quotaService;
    private final PromptScoringService scoringService;
    private final CurriculumMatchingService curriculumService;
    private final AiClientService geminiService;
    private final PromptVersionService versionService;
    private final PromptScoreRepository scoreRepository;
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
                LOCK_TIMEOUT);

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
                    .orElseThrow(
                            () -> new ResourceNotFoundException("prompt not found with id: " + request.promptId()));

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
            // publish event to redis
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
                pageable);

        return queuePage.map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OptimizationQueueResponse> getPromptOptimizationHistory(UUID promptId, UUID userId, Pageable pageable) {
        log.info("Fetching optimization history for prompt: {} by user: {}", promptId, userId);

        Page<OptimizationQueue> queuePage = queueRepository.findByPromptIdAndRequestedByIdOrderByCreatedAtDesc(
                promptId,
                userId,
                pageable);

        return queuePage.map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OptimizationQueueResponse> getPendingOptimizations(UUID userId) {
        log.info("Fetching pending optimizations for user: {}", userId);

        List<OptimizationQueue> pendingQueues = queueRepository.findByRequestedByIdAndStatusInOrderByCreatedAtDesc(
                userId,
                List.of(QueueStatus.PENDING.name(), QueueStatus.PROCESSING.name()));

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
                    queueId.toString());
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
                QueueStatus.PENDING.name(), pageable).getContent();

        // Re-trigger events for pending items
        for (OptimizationQueue item : pendingItems) {
            publishOptimizationEvent(item.getId());
        }
    }

    @Override
    public OptimizationResponse optimize(OptimizationRequest request) {
        log.info("Starting optimization. Scratchpad mode: {}", request.promptId() == null);

        // 1. Generate Cache Key
        String cacheKey = generateOptimizationCacheKey(request);
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("Returning cached optimization result");
                return objectMapper.readValue(cached, OptimizationResponse.class);
            }
        } catch (Exception e) {
            log.warn("Cache read failed", e);
        }

        // 2. Score Original Prompt
        PromptScoreResult originalScore = scoringService.scorePrompt(
                request.promptContent(),
                request.lessonId());

        log.info("Original prompt score: {}", originalScore.overallScore());

        // 3. Resolve Context
        CurriculumContextDetail curriculumContext = resolveCurriculumContext(
                request.promptContent(),
                request.lessonId(),
                originalScore.detectedContext());

        String contextString = buildCurriculumContextString(curriculumContext);

        // 4. Optimize via AI
        String optimizedPrompt = geminiService.optimizePrompt(
                request.promptContent(),
                request.optimizationMode(),
                contextString,
                request.selectedWeaknesses(),
                request.customInstruction());

        log.info("Generated optimized prompt");

        // 5. Score Optimized Prompt
        PromptScoreResult optimizedScore = scoringService.scorePrompt(
                optimizedPrompt,
                curriculumContext.lessonId());

        log.info("Optimized prompt score: {}", optimizedScore.overallScore());

        List<String> appliedFixes = identifyAppliedFixes(originalScore, optimizedScore);
        double improvement = optimizedScore.overallScore() - originalScore.overallScore();

        // 6. Construct Response (No DB Save)
        OptimizationResponse response = new OptimizationResponse(
                null, // versionId is null for scratchpad/unsaved
                request.promptContent(),
                optimizedPrompt,
                originalScore,
                optimizedScore,
                improvement,
                curriculumContext,
                appliedFixes,
                Instant.now());

        // 7. Cache Result
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), Duration.ofHours(24));
        } catch (Exception e) {
            log.warn("Cache write failed", e);
        }

        return response;
    }

    private String generateOptimizationCacheKey(OptimizationRequest request) {
        String input = request.promptContent() +
                "|" + request.optimizationMode() +
                "|" + request.lessonId() +
                "|" + request.customInstruction() +
                "|" + (request.selectedWeaknesses() != null ? request.selectedWeaknesses().toString() : "");
        return "opt:" + hashString(input);
    }

    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    @Override
    public OptimizationResponse getOptimizationResult(UUID versionId) {
        PromptVersion version = versionService.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("prompt version not found with id: " + versionId));

        if (!version.getIsAiGenerated()) {
            throw new InvalidActionException("Version is not AI-generated");
        }

        PromptScore optimizedScore = scoreRepository.findByVersionId(versionId)
                .orElseThrow(() -> new RuntimeException("Score not found for version"));

        PromptScore originalScore = scoreRepository.findByPromptIdAndVersionIdIsNull(version.getPromptId())
                .orElse(null);

        CurriculumContextDetail curriculumContext = null;
        if (version.getPrompt().getLessonId() != null) {
            curriculumContext = curriculumService.getContextDetail(version.getPrompt().getLessonId());
        }

        return OptimizationResponse.builder()
                .versionId(version.getId())
                .originalPrompt(originalScore != null ? version.getPrompt().getInstruction() : null)
                .optimizedPrompt(version.getInstruction())
                .originalScore(convertToScoreResult(originalScore))
                .optimizedScore(convertToScoreResult(optimizedScore))
                .improvement(
                        optimizedScore.getOverallScore().doubleValue() -
                                (originalScore != null ? originalScore.getOverallScore().doubleValue() : 0))
                .curriculumContext(curriculumContext)
                .appliedFixes(List.of()) // Applied fixes would need to be stored separately
                .createdAt(version.getCreatedAt())
                .build();

    }

    private CurriculumContextDetail resolveCurriculumContext(String promptText, UUID lessonId,
            CurriculumContext detectedContext) {
        if (lessonId != null) {
            return curriculumService.getContextDetail(lessonId);
        }

        if (detectedContext.getSubjectId() != null && detectedContext.getGradeLevel() != null) {
            LessonSuggestion suggestion = curriculumService.suggestLesson(
                    promptText,
                    detectedContext.getSubjectId(),
                    detectedContext.getGradeLevel());

            if (suggestion != null && suggestion.confidence() > 0.5) {
                log.info("Auto-detected lesson: {}", suggestion.lessonName());
                return curriculumService.getContextDetail(suggestion.lessonId());
            }
        }

        // Fallback context if no lesson identified
        return new CurriculumContextDetail(
                null, "General", "General Content", 0, "General", 0, 1, 0, "General Topic");
    }

    private String buildCurriculumContextString(CurriculumContextDetail context) {
        if (context.lessonId() == null)
            return "General Context";
        return String.format("""
                Môn học: %s
                Khối: %d
                Học kỳ: %d
                Chương %d: %s
                Bài %d: %s

                Nội dung bài học:
                %s
                """,
                context.subjectName(),
                context.gradeLevel(),
                context.semester(),
                context.chapterNumber(),
                context.chapterName(),
                context.lessonNumber(),
                context.lessonName(),
                context.lessonContent());
    }

    private List<String> identifyAppliedFixes(PromptScoreResult original, PromptScoreResult optimized) {
        List<String> fixes = new ArrayList<>();

        if (optimized.instructionClarity().score() > original.instructionClarity().score() + 10) {
            fixes.add("Improved instruction clarity");
        }

        if (optimized.contextCompleteness().score() > original.contextCompleteness().score() + 10) {
            fixes.add("Added missing context information");
        }

        if (optimized.outputSpecification().score() > original.outputSpecification().score() + 10) {
            fixes.add("Defined output format and structure");
        }

        if (optimized.constraintStrength().score() > original.constraintStrength().score() + 10) {
            fixes.add("Added constraints and guardrails");
        }

        if (optimized.curriculumAlignment().score() > original.curriculumAlignment().score() + 10) {
            fixes.add("Aligned with curriculum objectives");
        }

        if (optimized.pedagogicalQuality().score() > original.pedagogicalQuality().score() + 10) {
            fixes.add("Enhanced pedagogical approach");
        }

        return fixes;
    }

    private PromptScoreResult convertToScoreResult(PromptScore score) {
        if (score == null)
            return null;

        @SuppressWarnings("unchecked")
        Map<String, List<String>> weaknesses = (Map<String, List<String>>) (Map<?, ?>) score.getDetectedWeaknesses();

        return new PromptScoreResult(
                score.getOverallScore().doubleValue(),
                new DimensionScore("Instruction Clarity", score.getInstructionClarityScore().doubleValue(),
                        100.0, null, null, List.of(), List.of()),
                new DimensionScore("Context Completeness", score.getContextCompletenessScore().doubleValue(),
                        100.0, null, null, List.of(), List.of()),
                new DimensionScore("Output Specification", score.getOutputSpecificationScore().doubleValue(),
                        100.0, null, null, List.of(), List.of()),
                new DimensionScore("Constraint Strength", score.getConstraintStrengthScore().doubleValue(),
                        100.0, null, null, List.of(), List.of()),
                new DimensionScore("Curriculum Alignment", score.getCurriculumAlignmentScore().doubleValue(),
                        100.0, null, null, List.of(), List.of()),
                new DimensionScore("Pedagogical Quality", score.getPedagogicalQualityScore().doubleValue(),
                        100.0, null, null, List.of(), List.of()),
                weaknesses != null ? weaknesses : new HashMap<>(),
                null);
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

    private Map<String, Object> convertContextToMap(CurriculumContext context) {
        Map<String, Object> map = new HashMap<>();
        if (context.getSubject() != null)
            map.put("subject", context.getSubject());
        if (context.getGradeLevel() != null)
            map.put("gradeLevel", context.getGradeLevel());
        if (context.getSemester() != null)
            map.put("semester", context.getSemester());
        if (context.getDetectedKeywords() != null)
            map.put("keywords", context.getDetectedKeywords());
        return map;
    }
}