package SEP490.EduPrompt.service.prompt;

import SEP490.EduPrompt.dto.request.prompt.SaveOptimizedPromptRequest;
import SEP490.EduPrompt.dto.response.prompt.PromptVersionResponse;

import java.util.UUID;

public interface PromptVersionService {
    PromptVersionResponse saveOptimizedPromptAsVersion(UUID userId, SaveOptimizedPromptRequest request);

    // this might be not needed, instead of save new prompt as version, why dont we just create a new one :D
//    PromptVersionResponse saveNewPromptAsVersion(UUID userId, Prompt prompt);

    PromptVersionResponse getPromptVersion(UUID versionId);
}
