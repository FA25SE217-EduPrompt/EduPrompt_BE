package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.response.prompt.PromptResponse;

import java.util.List;
import java.util.UUID;

public interface PromptRecommendService {

    List<PromptResponse> getRecommendedPrompts(UUID userId);
}
