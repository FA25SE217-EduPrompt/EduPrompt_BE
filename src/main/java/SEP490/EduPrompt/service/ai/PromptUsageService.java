package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.request.prompt.PromptTestRequest;
import SEP490.EduPrompt.dto.response.prompt.ClientPromptResponse;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.model.PromptUsage;

import java.util.UUID;

public interface PromptUsageService {
    PromptUsage saveUsage(
            UUID userId,
            Prompt prompt,
            PromptTestRequest request,
            ClientPromptResponse aiResponse,
            int tokensUsed,
            long executionTime,
            String idempotencyKey);
}
