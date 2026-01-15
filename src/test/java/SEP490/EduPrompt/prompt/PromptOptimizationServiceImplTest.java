package SEP490.EduPrompt.prompt;

import SEP490.EduPrompt.dto.request.prompt.OptimizationRequest;
import SEP490.EduPrompt.dto.request.prompt.PromptOptimizationRequest;
import SEP490.EduPrompt.dto.response.curriculum.CurriculumContext;
import SEP490.EduPrompt.dto.response.curriculum.CurriculumContextDetail;
import SEP490.EduPrompt.dto.response.curriculum.DimensionScore;
import SEP490.EduPrompt.dto.response.prompt.OptimizationQueueResponse;
import SEP490.EduPrompt.dto.response.prompt.OptimizationResponse;
import SEP490.EduPrompt.dto.response.prompt.PromptScoreResult;
import SEP490.EduPrompt.enums.AiModel;
import SEP490.EduPrompt.enums.OptimizationMode;
import SEP490.EduPrompt.enums.QueueStatus;
import SEP490.EduPrompt.enums.QuotaType;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.OptimizationQueue;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.repo.OptimizationQueueRepository;
import SEP490.EduPrompt.repo.PromptRepository;
import SEP490.EduPrompt.repo.PromptScoreRepository;
import SEP490.EduPrompt.repo.UserRepository;
import SEP490.EduPrompt.service.ai.AiClientService;
import SEP490.EduPrompt.service.ai.PromptOptimizationServiceImpl;
import SEP490.EduPrompt.service.ai.QuotaService;
import SEP490.EduPrompt.service.curriculum.CurriculumMatchingService;
import SEP490.EduPrompt.service.prompt.PromptScoringService;
import SEP490.EduPrompt.service.prompt.PromptVersionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromptOptimizationServiceImplTest {

    @Mock private QuotaService quotaService;
    @Mock private PromptScoringService scoringService;
    @Mock private CurriculumMatchingService curriculumService;
    @Mock private AiClientService geminiService;
    @Mock private PromptVersionService versionService;
    @Mock private PromptScoreRepository scoreRepository;
    @Mock private PromptRepository promptRepository;
    @Mock private OptimizationQueueRepository queueRepository;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private UserRepository userRepository;
    @Mock private TransactionTemplate transactionTemplate;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PromptOptimizationServiceImpl optimizationService;

    private UUID userId;
    private UUID promptId;
    private UUID queueId;
    private User user;
    private Prompt prompt;
    private OptimizationQueue optimizationQueue;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        promptId = UUID.randomUUID();
        queueId = UUID.randomUUID();

        user = User.builder().id(userId).email("test@example.com").build();
        prompt = Prompt.builder().id(promptId).userId(userId).instruction("Original Instruction").build();

        optimizationQueue = OptimizationQueue.builder()
                .id(queueId)
                .prompt(prompt)
                .promptId(promptId)
                .requestedBy(user)
                .requestedById(userId)
                .status(QueueStatus.PENDING.name())
                .idempotencyKey("test-key")
                .retryCount(0)
                .maxRetries(2)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Mock Redis Operations
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ======================================================================//
    // ======================= REQUEST OPTIMIZATION =========================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Request Optimization - Success (Fresh Request)")
    void requestOptimization_WhenFreshRequest_ShouldQueueAndPublishEvent() {
        // Arrange
        String idempotencyKey = "unique-key-123";
        PromptOptimizationRequest request = new PromptOptimizationRequest(
                promptId,
                AiModel.GPT_4O_MINI,
                "Make it better",
                0.7,
                1000,
                0.9
        );

        // Redis Lock & Cache Miss
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn(null);

        // DB Checks
        when(queueRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(userRepository.getReferenceById(userId)).thenReturn(user);

        // Transaction Handling
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });
        when(queueRepository.save(any(OptimizationQueue.class))).thenReturn(optimizationQueue);

        // Act
        OptimizationQueueResponse response = optimizationService.requestOptimization(userId, request, idempotencyKey);

        // Assert
        assertNotNull(response);
        assertEquals(QueueStatus.PENDING, response.status());
        verify(quotaService).validateQuota(userId, QuotaType.OPTIMIZATION, 1000);
        verify(redisTemplate).convertAndSend(eq("queue:optimization"), anyString());
        verify(redisTemplate).delete(contains("lock:")); // Lock released
    }

    @Test
    @DisplayName("Case 2: Request Optimization - Success (Idempotency Cache Hit)")
    void requestOptimization_WhenCached_ShouldReturnCacheAndSkipDB() throws Exception {
        // Arrange
        String idempotencyKey = "cached-key";
        PromptOptimizationRequest request = new PromptOptimizationRequest(
                promptId, AiModel.GPT_4O_MINI, "Input", 0.7, 100, 0.9
        );

        // 1. Create the expected response object
        OptimizationQueueResponse cachedRes = OptimizationQueueResponse.builder()
                .id(queueId)
                .status(QueueStatus.COMPLETED)
                .retryCount(0)
                .build();

        // 2. Convert it to a valid JSON string using the spy ObjectMapper
        // This ensures Jackson won't crash when parsing it later
        String validJson = objectMapper.writeValueAsString(cachedRes);

        // 3. Mock Redis to return the VALID JSON string
        when(valueOperations.get(contains(idempotencyKey))).thenReturn(validJson);

        // Note: We do NOT need to stub objectMapper.readValue() because it is a @Spy.
        // It will naturally parse the validJson string correctly.

        // Act
        OptimizationQueueResponse response = optimizationService.requestOptimization(userId, request, idempotencyKey);

        // Assert
        assertNotNull(response);
        assertEquals(QueueStatus.COMPLETED, response.status());
        assertEquals(queueId, response.id());

        // Verify DB was skipped
        verify(queueRepository, never()).save(any());
        // Verify no new event was published
        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    @DisplayName("Case 3: Request Optimization - Fail (Concurrent Lock)")
    void requestOptimization_WhenLocked_ShouldThrowInvalidInput() {
        // Arrange
        String idempotencyKey = "locked-key";
        PromptOptimizationRequest request = new PromptOptimizationRequest(promptId, AiModel.GPT_4O_MINI, "Input", 0.7, 100, 0.9);

        // Lock fail
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);

        // Act & Assert
        assertThrows(InvalidInputException.class,
                () -> optimizationService.requestOptimization(userId, request, idempotencyKey));
    }
    // ======================================================================//
    // ====================== GET OPTIMIZATION STATUS =======================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Get Status - Success (Owner Matches)")
    void getOptimizationStatus_WhenOwnerMatches_ShouldReturnStatus() {
        // Arrange
        // IMPORTANT: Ensure retryCount is initialized to avoid NPE in the mapper
        optimizationQueue.setRetryCount(0);
        optimizationQueue.setMaxRetries(3);

        when(queueRepository.findById(queueId)).thenReturn(Optional.of(optimizationQueue));

        // Act
        OptimizationQueueResponse response = optimizationService.getOptimizationStatus(queueId, userId);

        // Assert
        assertNotNull(response);
        assertEquals(queueId, response.id());
        assertEquals(QueueStatus.PENDING, response.status()); //
        assertEquals(0, response.retryCount());
    }

    @Test
    @DisplayName("Case 2: Get Status - Fail (Queue Not Found)")
    void getOptimizationStatus_WhenQueueNotFound_ShouldThrowResourceNotFound() {
        // Arrange
        when(queueRepository.findById(queueId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> optimizationService.getOptimizationStatus(queueId, userId));
    }

    @Test
    @DisplayName("Case 3: Get Status - Fail (Access Denied - Different User)")
    void getOptimizationStatus_WhenUserNotOwner_ShouldThrowResourceNotFound() {
        // Arrange
        UUID otherUserId = UUID.randomUUID();
        // The queue belongs to 'userId' (from setUp), but we request with 'otherUserId'
        when(queueRepository.findById(queueId)).thenReturn(Optional.of(optimizationQueue));

        // Act & Assert
        // The service intentionally throws ResourceNotFoundException to hide the resource from non-owners
        assertThrows(ResourceNotFoundException.class,
                () -> optimizationService.getOptimizationStatus(queueId, otherUserId));
    }

    // ======================================================================//
    // ==================== GET PENDING OPTIMIZATIONS =======================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Get Pending - Success (Returns Pending & Processing)")
    void getPendingOptimizations_WhenItemsExist_ShouldReturnList() {
        // Arrange
        // Create two items: one PENDING, one PROCESSING
        OptimizationQueue item1 = OptimizationQueue.builder()
                .id(UUID.randomUUID())
                .status(QueueStatus.PENDING.name())
                .retryCount(0) // Initialize to prevent NPE
                .maxRetries(3)
                .requestedById(userId)
                .build();

        OptimizationQueue item2 = OptimizationQueue.builder()
                .id(UUID.randomUUID())
                .status(QueueStatus.PROCESSING.name())
                .retryCount(1)
                .maxRetries(3)
                .requestedById(userId)
                .build();

        // Expect repository call with correct Status list
        List<String> expectedStatuses = List.of(QueueStatus.PENDING.name(), QueueStatus.PROCESSING.name());

        when(queueRepository.findByRequestedByIdAndStatusInOrderByCreatedAtDesc(eq(userId), eq(expectedStatuses)))
                .thenReturn(List.of(item1, item2));

        // Act
        List<OptimizationQueueResponse> response = optimizationService.getPendingOptimizations(userId);

        // Assert
        assertNotNull(response);
        assertEquals(2, response.size());

        // Verify mapping
        assertTrue(response.stream().anyMatch(r -> r.status() == QueueStatus.PENDING));
        assertTrue(response.stream().anyMatch(r -> r.status() == QueueStatus.PROCESSING));
    }

    @Test
    @DisplayName("Case 2: Get Pending - Success (Empty List)")
    void getPendingOptimizations_WhenNoItems_ShouldReturnEmpty() {
        // Arrange
        when(queueRepository.findByRequestedByIdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        List<OptimizationQueueResponse> response = optimizationService.getPendingOptimizations(userId);

        // Assert
        assertNotNull(response);
        assertTrue(response.isEmpty());
    }
}
