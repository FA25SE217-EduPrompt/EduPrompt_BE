package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.request.prompt.OptimizationRequest;
import SEP490.EduPrompt.dto.request.prompt.PromptOptimizationRequest;
import SEP490.EduPrompt.dto.response.prompt.OptimizationQueueResponse;
import SEP490.EduPrompt.dto.response.prompt.OptimizationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PromptOptimizationService {
    OptimizationQueueResponse requestOptimization(UUID userId, PromptOptimizationRequest request, String idempotencyKey);

    OptimizationQueueResponse getOptimizationStatus(UUID queueId, UUID userId);

    void processOptimizationQueue();

    Page<OptimizationQueueResponse> getUserOptimizationHistory(UUID userId, Pageable pageable);

    Page<OptimizationQueueResponse> getPromptOptimizationHistory(UUID promptId, UUID userId, Pageable pageable);

    List<OptimizationQueueResponse> getPendingOptimizations(UUID userId);

    OptimizationQueueResponse retryOptimization(UUID queueId, UUID userId);

    void cancelOptimization(UUID queueId, UUID userId);

    //new optimization
    OptimizationResponse optimize(OptimizationRequest request);

    OptimizationResponse getOptimizationResult(UUID versionId);
}
