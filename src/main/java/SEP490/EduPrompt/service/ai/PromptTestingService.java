package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.request.prompt.PromptTestRequest;
import SEP490.EduPrompt.dto.response.prompt.PromptTestResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PromptTestingService {
    PromptTestResponse testPrompt(UUID userId, PromptTestRequest request, String idempotencyKey);

    PromptTestResponse getTestResult(UUID usageId);

    Page<PromptTestResponse> getUserTestHistory(UUID userId, Pageable pageable);
}
