package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.request.prompt.PromptTestRequest;
import SEP490.EduPrompt.dto.response.prompt.PromptTestResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PromptTestingService {
    PromptTestResponse testPrompt(UUID userId, PromptTestRequest request, String idempotencyKey);

//    PromptTestResponse testPromptSync(UUID userId, PromptTestRequest request, String idempotencyKey);
//
//    PromptTestResponse testPromptAsync(UUID userId, PromptTestRequest request, String idempotencyKey);

    PromptTestResponse getTestResult(UUID usageId);

    List<PromptTestResponse> getTestResultsByPromptId(UUID promptId);

    Page<PromptTestResponse> getUserTestHistory(UUID userId, Pageable pageable);

    Page<PromptTestResponse> getPromptTestHistory(UUID promptId, UUID userId, Pageable pageable);

    void deleteTestResult(UUID usageId, UUID userId);
}
