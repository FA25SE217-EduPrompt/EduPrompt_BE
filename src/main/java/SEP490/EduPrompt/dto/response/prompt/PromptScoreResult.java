package SEP490.EduPrompt.dto.response.prompt;

import SEP490.EduPrompt.dto.response.curriculum.CurriculumContext;
import SEP490.EduPrompt.dto.response.curriculum.DimensionScore;

import java.util.List;

public record PromptScoreResult(
        Double overallScore,
        DimensionScore instructionClarity,
        DimensionScore contextCompleteness,
        DimensionScore outputSpecification,
        DimensionScore constraintStrength,
        DimensionScore curriculumAlignment,
        DimensionScore pedagogicalQuality,
        List<String> weaknesses,
        CurriculumContext detectedContext
) {}