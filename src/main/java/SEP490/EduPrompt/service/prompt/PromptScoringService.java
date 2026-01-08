package SEP490.EduPrompt.service.prompt;

import SEP490.EduPrompt.dto.response.prompt.PromptScoreResult;

import java.util.UUID;

public interface PromptScoringService {
    PromptScoreResult scorePrompt(String promptText, UUID lessonId);

    void savePromptScore(UUID promptId, UUID versionId, PromptScoreResult scoreResult);

    void savePromptScoreAsync(UUID promptId, UUID versionId, PromptScoreResult scoreResult);

    void scoreAndSaveAsync(UUID promptId, UUID versionId, String promptText, UUID lessonId);

    PromptScoreResult scorePromptWithQuota(UUID userId, String promptText, UUID lessonId);
}
