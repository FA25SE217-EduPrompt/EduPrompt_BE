package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.response.prompt.ClientPromptResponse;
import SEP490.EduPrompt.enums.AiModel;
import SEP490.EduPrompt.enums.QueueStatus;
import SEP490.EduPrompt.enums.QuotaType;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.AiSuggestionLog;
import SEP490.EduPrompt.model.OptimizationQueue;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.model.PromptUsage;
import SEP490.EduPrompt.repo.AiSuggestionLogRepository;
import SEP490.EduPrompt.repo.OptimizationQueueRepository;
import SEP490.EduPrompt.repo.PromptRepository;
import SEP490.EduPrompt.repo.PromptUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Event-driven queue processor
 * Listens to Redis Pub/Sub and processes items immediately
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class QueueEventListener {

    private static final int AI_CALL_TIMEOUT_SECONDS = 60;

    private final OptimizationQueueRepository queueRepository;
    private final PromptUsageRepository usageRepository;
    private final PromptRepository promptRepository;
    private final AiSuggestionLogRepository suggestionRepository;
    private final AiClientService aiClientService;
    private final QuotaService quotaService;
    private final TransactionTemplate transactionTemplate;

    /**
     * Called when optimization request is queued
     * Message format: "queueId"
     */
    public void onOptimizationQueued(String message) {
        try {
            UUID queueId = UUID.fromString(message);
            log.info("Received optimization event for queue: {}", queueId);

            processOptimizationItem(queueId);

        } catch (Exception e) {
            log.error("Failed to process optimization event: {}", message, e);
        }
    }

    /**
     * Called when test request is queued
     * Message format: "usageId"
     */
    public void onTestQueued(String message) {
        try {
            UUID usageId = UUID.fromString(message);
            log.info("Received test event for usage: {}", usageId);

            processTestItem(usageId);

        } catch (Exception e) {
            log.error("Failed to process test event: {}", message, e);
        }
    }

    /**
     * Process optimization item - Load data in transaction, then call AI
     */
    private void processOptimizationItem(UUID queueId) {
        // load data and update status in transaction
        OptimizationQueueData queueData = transactionTemplate.execute(status -> {
            OptimizationQueue item = queueRepository.findById(queueId).orElse(null);
            if (item == null) {
                log.warn("Optimization queue item not found: {}", queueId);
                return null;
            }

            // Check if already processed
            if (!QueueStatus.PENDING.name().equals(item.getStatus())) {
                log.info("Item {} already processed, status: {}", queueId, item.getStatus());
                return null;
            }

            // Mark as PROCESSING
            item.setStatus(QueueStatus.PROCESSING.name());
            item.setUpdatedAt(Instant.now());
            queueRepository.saveAndFlush(item);

            // Fetch prompt inside transaction
            Prompt prompt = promptRepository.findById(item.getPromptId())
                    .orElseThrow(() -> new ResourceNotFoundException("prompt not found"));

            log.info("Processing optimization item: {}", queueId);

            // Return data transfer object
            return OptimizationQueueData.builder()
                    .queueId(item.getId())
                    .userId(item.getRequestedById())
                    .promptId(prompt.getId())
                    .prompt(prompt) // Pass the loaded entity
                    .input(item.getInput())
                    .temperature(item.getTemperature())
                    .maxTokens(item.getMaxTokens())
                    .aiModel(item.getAiModel())
                    .build();
        });

        if (queueData == null) {
            return; // Already processed or not found
        }

        // process outside transaction
        try {
            processOptimizationLogic(queueData);
        } catch (Exception e) {
            log.error("Failed to process optimization: {}", queueId, e);
            handleOptimizationFailure(queueId, e.getMessage());
        }
    }

    /**
     * Core optimization logic (outside transaction)
     */
    private void processOptimizationLogic(OptimizationQueueData data) {
        int reservedTokens = data.maxTokens;

        try {
            // Reserve quota
            quotaService.validateAndDecrementQuota(data.userId, QuotaType.OPTIMIZATION, reservedTokens);

            // Call AI with timeout
            ClientPromptResponse response = callAiWithTimeout(() ->
                    aiClientService.optimizePrompt(
                            data.prompt,
                            data.input,
                            data.temperature,
                            data.maxTokens
                    )
            );

            int tokensUsed = response.totalTokens();

            // Refund unused tokens
            int tokensToRefund = reservedTokens - tokensUsed;
            if (tokensToRefund > 0) {
                quotaService.refundTokens(data.userId, tokensToRefund);
                log.debug("Refunded {} unused tokens", tokensToRefund);
            }

            // save results in new transaction
            transactionTemplate.executeWithoutResult(status -> {
                OptimizationQueue item = queueRepository.findById(data.queueId)
                        .orElseThrow(() -> new ResourceNotFoundException("Queue not found"));

                Prompt prompt = promptRepository.findById(data.promptId)
                        .orElseThrow(() -> new ResourceNotFoundException("Prompt not found"));

                // Save suggestion log
                AiSuggestionLog suggestionLog = AiSuggestionLog.builder()
                        .prompt(prompt)
                        .promptId(prompt.getId())
                        .requestedBy(data.userId)
                        .input(data.input)
                        .output(response.content())
                        .aiModel(data.aiModel)
                        .status(QueueStatus.COMPLETED.name())
                        .optimizationQueue(item)
                        .createdAt(Instant.now())
                        .build();

                suggestionRepository.save(suggestionLog);

                // Update queue status
                item.setOutput(response.content());
                item.setStatus(QueueStatus.COMPLETED.name());
                item.setUpdatedAt(Instant.now());
                queueRepository.save(item);
            });

            log.info("Optimization completed: {}", data.queueId);

        } catch (Exception e) {
            log.error("AI call failed, refunding quota", e);
            quotaService.refundQuota(data.userId, QuotaType.OPTIMIZATION, reservedTokens);
            throw e;
        }
    }

    /**
     * Process test item - Load data in transaction, then call AI
     */
    private void processTestItem(UUID usageId) {
        // load data and update status in transaction
        TestUsageData usageData = transactionTemplate.execute(status -> {
            PromptUsage usage = usageRepository.findById(usageId).orElse(null);
            if (usage == null) {
                log.warn("Test usage not found: {}", usageId);
                return null;
            }

            // Check if already processed
            if (!QueueStatus.PENDING.name().equals(usage.getStatus())) {
                log.info("Usage {} already processed, status: {}", usageId, usage.getStatus());
                return null;
            }

            // Mark as PROCESSING
            usage.setStatus(QueueStatus.PROCESSING.name());
            usage.setUpdatedAt(Instant.now());
            usageRepository.saveAndFlush(usage);

            // Fetch prompt inside transaction
            Prompt prompt = promptRepository.findById(usage.getPromptId())
                    .orElseThrow(() -> new ResourceNotFoundException("prompt not found"));

            log.info("Processing test item: {}", usageId);

            // Return data transfer object
            return TestUsageData.builder()
                    .usageId(usage.getId())
                    .userId(usage.getUserId())
                    .promptId(prompt.getId())
                    .prompt(prompt) // Pass the loaded entity
                    .aiModel(usage.getAiModel())
                    .inputText(usage.getInputText())
                    .temperature(usage.getTemperature())
                    .maxTokens(usage.getMaxTokens())
                    .topP(usage.getTopP())
                    .build();
        });

        if (usageData == null) {
            return; // Already processed or not found
        }

        // process outside transaction
        try {
            processTestLogic(usageData);
        } catch (Exception e) {
            log.error("Failed to process test: {}", usageId, e);
            handleTestFailure(usageId, e.getMessage());
        }
    }

    /**
     * Core test logic (outside transaction)
     */
    private void processTestLogic(TestUsageData data) {
        int reservedTokens = data.maxTokens;

        try {
            // Reserve quota
            quotaService.validateAndDecrementQuota(data.userId, QuotaType.TEST, reservedTokens);

            // Call AI with timeout
            long startTime = System.currentTimeMillis();
            ClientPromptResponse response = callAiWithTimeout(() ->
                    aiClientService.testPrompt(
                            data.prompt,
                            AiModel.parseAiModel(data.aiModel),
                            data.inputText,
                            data.temperature,
                            data.maxTokens,
                            data.topP
                    )
            );

            int executionTime = (int) (System.currentTimeMillis() - startTime);
            int tokensUsed = response.totalTokens();

            // Refund unused tokens
            int tokensToRefund = reservedTokens - tokensUsed;
            if (tokensToRefund > 0) {
                quotaService.refundTokens(data.userId, tokensToRefund);
            }

            // save results in new transaction
            transactionTemplate.executeWithoutResult(status -> {
                PromptUsage usage = usageRepository.findById(data.usageId)
                        .orElseThrow(() -> new ResourceNotFoundException("Usage not found"));

                usage.setOutput(response.content());
                usage.setTokensUsed(tokensUsed);
                usage.setExecutionTimeMs(executionTime);
                usage.setStatus(QueueStatus.COMPLETED.name());
                usage.setUpdatedAt(Instant.now());
                usageRepository.save(usage);
            });

            log.info("Test completed: {}", data.usageId);

        } catch (Exception e) {
            log.error("AI call failed, refunding quota", e);
            quotaService.refundQuota(data.userId, QuotaType.TEST, reservedTokens);
            throw e;
        }
    }

    /**
     * Handle optimization failure
     */
    private void handleOptimizationFailure(UUID queueId, String errorMessage) {
        transactionTemplate.executeWithoutResult(status -> {
            OptimizationQueue item = queueRepository.findById(queueId).orElse(null);
            if (item == null) return;

            item.setRetryCount(item.getRetryCount() + 1);
            item.setErrorMessage(errorMessage);
            item.setUpdatedAt(Instant.now());

            if (item.getRetryCount() >= item.getMaxRetries()) {
                log.warn("Max retries reached for: {}", queueId);
                item.setStatus(QueueStatus.FAILED.name());
            } else {
                log.info("Retry {}/{} for: {}", item.getRetryCount(), item.getMaxRetries(), queueId);
                item.setStatus(QueueStatus.PENDING.name());
            }

            queueRepository.save(item);
        });
    }

    /**
     * Handle test failure
     */
    private void handleTestFailure(UUID usageId, String errorMessage) {
        transactionTemplate.executeWithoutResult(status -> {
            PromptUsage usage = usageRepository.findById(usageId).orElse(null);
            if (usage == null) return;

            usage.setStatus(QueueStatus.FAILED.name());
            usage.setErrorMessage(errorMessage);
            usage.setUpdatedAt(Instant.now());
            usageRepository.save(usage);
        });
    }

    /**
     * Call AI with timeout protection
     */
    private ClientPromptResponse callAiWithTimeout(AiCallSupplier supplier) {
        try {
            CompletableFuture<ClientPromptResponse> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return supplier.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            return future.get(AI_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        } catch (java.util.concurrent.TimeoutException e) {
            throw new RuntimeException("AI request timed out after " + AI_CALL_TIMEOUT_SECONDS + " seconds");
        } catch (Exception e) {
            throw new RuntimeException("AI call failed: " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface AiCallSupplier {
        ClientPromptResponse get() throws Exception;
    }

    // ===== Data Transfer Objects =====

    @lombok.Builder
    @lombok.Getter
    private static class OptimizationQueueData {
        UUID queueId;
        UUID userId;
        UUID promptId;
        Prompt prompt;
        String input;
        Double temperature;
        int maxTokens;
        String aiModel;
    }

    @lombok.Builder
    @lombok.Getter
    private static class TestUsageData {
        UUID usageId;
        UUID userId;
        UUID promptId;
        Prompt prompt;
        String aiModel;
        String inputText;
        Double temperature;
        int maxTokens;
        Double topP;
    }
}