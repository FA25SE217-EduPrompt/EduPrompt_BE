package SEP490.EduPrompt.dto.response.prompt;

import SEP490.EduPrompt.dto.response.curriculum.CurriculumContextDetail;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record OptimizationResponse(
        UUID versionId,
        String originalPrompt,
        String optimizedPrompt,
        PromptScoreResult originalScore,
        PromptScoreResult optimizedScore,
        Double improvement,
        CurriculumContextDetail curriculumContext,
        List<String> appliedFixes,
        Instant createdAt
) {}
