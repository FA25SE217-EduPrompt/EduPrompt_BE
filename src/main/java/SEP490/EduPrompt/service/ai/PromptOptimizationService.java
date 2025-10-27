package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.request.prompt.PromptOptimizationRequest;
import SEP490.EduPrompt.dto.response.prompt.OptimizationQueueResponse;

import java.util.UUID;

public interface PromptOptimizationService {
    OptimizationQueueResponse requestOptimization(UUID userId, PromptOptimizationRequest request, String idempotencyKey);

    OptimizationQueueResponse getOptimizationStatus(UUID queueId, UUID userId);

    void processOptimizationQueue();
}
