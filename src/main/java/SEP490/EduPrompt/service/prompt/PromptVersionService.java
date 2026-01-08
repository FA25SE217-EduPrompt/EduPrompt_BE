package SEP490.EduPrompt.service.prompt;

import SEP490.EduPrompt.dto.request.prompt.CreatePromptVersionRequest;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.model.PromptVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromptVersionService {
    // PromptVersionResponse saveOptimizedPromptAsVersion(UUID userId,
    // SaveOptimizedPromptRequest request);
    //
    // // this might be not needed, instead of save new prompt as version, why dont
    // we just create a new one :D
    //// PromptVersionResponse saveNewPromptAsVersion(UUID userId, Prompt prompt);
    //
    // PromptVersionResponse getPromptVersion(UUID versionId);

    PromptVersion createVersion(Prompt prompt, CreatePromptVersionRequest request, UUID editorId, UUID lessonId);

    Optional<PromptVersion> findById(UUID versionId);

    List<PromptVersion> getVersionHistory(UUID promptId);
}
