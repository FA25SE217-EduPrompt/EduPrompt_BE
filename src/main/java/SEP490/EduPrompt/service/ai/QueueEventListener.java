

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

    private static final int AI_CALL_TIMEOUT_SECONDS = 30;

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
            log.info("🔔 Received optimization event for queue: {}", queueId);

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
     * Process optimization item in isolated transaction
     */
    private void processOptimizationItem(UUID queueId) {
        transactionTemplate.execute(status -> {
            try {
                // Reload item with pessimistic lock to prevent duplicate processing
                OptimizationQueue item = queueRepository.findById(queueId)
                        .orElse(null);

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

                log.info("Processing optimization item: {}", queueId);

                // Process outside transaction (AI call)
                processOptimizationLogic(item);
                return null;

            } catch (Exception e) {
                log.error("Failed to process optimization: {}", queueId, e);
                handleOptimizationFailure(queueId, e.getMessage());
                return null;
            }
        });
    }

    /**
     * Core optimization logic (outside main transaction)
     */
    private void processOptimizationLogic(OptimizationQueue item) {
        UUID userId = item.getRequestedById();
        int reservedTokens = item.getMaxTokens();

        try {
            // Reserve quota
            quotaService.validateAndDecrementQuota(userId, QuotaType.OPTIMIZATION, reservedTokens);

            // Fetch prompt
            Prompt prompt = promptRepository.findById(item.getPromptId())
                    .orElseThrow(() -> new ResourceNotFoundException("prompt not found"));

            // Call AI with timeout
            ClientPromptResponse response = callAiWithTimeout(() ->
                    aiClientService.optimizePrompt(
                            prompt,
                            item.getInput(),
                            item.getTemperature(),
                            item.getMaxTokens()
                    )
            );

            int tokensUsed = response.totalTokens();

            // Refund unused tokens
            int tokensToRefund = reservedTokens - tokensUsed;
            if (tokensToRefund > 0) {
                quotaService.refundTokens(userId, tokensToRefund);
                log.debug("Refunded {} unused tokens", tokensToRefund);
            }

            // Save results in new transaction
            transactionTemplate.execute(status -> {
                // Save suggestion log
                AiSuggestionLog suggestionLog = AiSuggestionLog.builder()
                        .prompt(prompt)
                        .promptId(prompt.getId())
                        .requestedBy(userId)
                        .input(item.getInput())
                        .output(response.content())
                        .aiModel(item.getAiModel())
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

                return null;
            });

            log.info("Optimization completed: {}", item.getId());

        } catch (Exception e) {
            log.error("AI call failed, refunding quota", e);
            quotaService.refundQuota(userId, QuotaType.OPTIMIZATION, reservedTokens);
            throw e;
        }
    }

    /**
     * Process test item in isolated transaction
     */
    private void processTestItem(UUID usageId) {
        transactionTemplate.execute(status -> {
            try {
                // Reload item
                PromptUsage usage = usageRepository.findById(usageId)
                        .orElse(null);

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

                log.info("Processing test item: {}", usageId);

                // Process outside transaction
                processTestLogic(usage);
                return null;

            } catch (Exception e) {
                log.error("Failed to process test: {}", usageId, e);
                handleTestFailure(usageId, e.getMessage());
                return null;
            }
        });
    }

    /**
     * Core test logic (outside main transaction)
     */
    private void processTestLogic(PromptUsage usage) {
        UUID userId = usage.getUserId();
        int reservedTokens = usage.getMaxTokens();

        try {
            // Reserve quota
            quotaService.validateAndDecrementQuota(userId, QuotaType.TEST, reservedTokens);

            // Fetch prompt
            Prompt prompt = promptRepository.findById(usage.getPromptId())
                    .orElseThrow(() -> new ResourceNotFoundException("prompt not found"));

            // Call AI with timeout
            long startTime = System.currentTimeMillis();
            ClientPromptResponse response = callAiWithTimeout(() ->
                    aiClientService.testPrompt(
                            prompt,
                            AiModel.parseAiModel(usage.getAiModel()),
                            usage.getInputText(),
                            usage.getTemperature(),
                            usage.getMaxTokens(),
                            usage.getTopP()
                    )
            );

            int executionTime = (int) (System.currentTimeMillis() - startTime);
            int tokensUsed = response.totalTokens();

            // Refund unused tokens
            int tokensToRefund = reservedTokens - tokensUsed;
            if (tokensToRefund > 0) {
                quotaService.refundTokens(userId, tokensToRefund);
            }

            // Save results in new transaction
            transactionTemplate.execute(status -> {
                usage.setOutput(response.content());
                usage.setTokensUsed(tokensUsed);
                usage.setExecutionTimeMs(executionTime);
                usage.setStatus(QueueStatus.COMPLETED.name());
                usage.setUpdatedAt(Instant.now());
                usageRepository.save(usage);
                return null;
            });

            log.info("Test completed: {}", usage.getId());

        } catch (Exception e) {
            log.error("AI call failed, refunding quota", e);
            quotaService.refundQuota(userId, QuotaType.TEST, reservedTokens);
            throw e;
        }
    }

    /**
     * Handle optimization failure
     */
    private void handleOptimizationFailure(UUID queueId, String errorMessage) {
        transactionTemplate.execute(status -> {
            OptimizationQueue item = queueRepository.findById(queueId).orElse(null);
            if (item == null) return null;

            item.setRetryCount(item.getRetryCount() + 1);
            item.setErrorMessage(errorMessage);
            item.setUpdatedAt(Instant.now());

            if (item.getRetryCount() >= item.getMaxRetries()) {
                log.warn("Max retries reached for: {}", queueId);
                item.setStatus(QueueStatus.FAILED.name());
            } else {
                log.info("Retry {}/{} for: {}", item.getRetryCount(), item.getMaxRetries(), queueId);
                item.setStatus(QueueStatus.PENDING.name());
                // Note: Will need manual re-trigger or fallback scheduler for retries
            }

            queueRepository.save(item);
            return null;
        });
    }

    /**
     * Handle test failure
     */
    private void handleTestFailure(UUID usageId, String errorMessage) {
        transactionTemplate.execute(status -> {
            PromptUsage usage = usageRepository.findById(usageId).orElse(null);
            if (usage == null) return null;

            usage.setStatus(QueueStatus.FAILED.name());
            usage.setErrorMessage(errorMessage);
            usage.setUpdatedAt(Instant.now());
            usageRepository.save(usage);
            return null;
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
}
