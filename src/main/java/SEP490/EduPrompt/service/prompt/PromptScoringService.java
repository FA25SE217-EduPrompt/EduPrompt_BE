package SEP490.EduPrompt.service.prompt;

import SEP490.EduPrompt.dto.response.prompt.PromptScoreResult;

import java.util.UUID;

public interface PromptScoringService {
    PromptScoreResult scorePrompt(String promptText, UUID lessonId);

    void savePromptScore(UUID promptId, UUID versionId, PromptScoreResult scoreResult);

    void savePromptScoreAsync(UUID promptId, UUID versionId, PromptScoreResult scoreResult);
}
