package SEP490.EduPrompt.dto.response.prompt;

import SEP490.EduPrompt.dto.response.curriculum.CurriculumContext;
import SEP490.EduPrompt.dto.response.curriculum.DimensionScore;

import java.util.List;
import java.util.Map;

public record PromptScoreResult(
                Double overallScore,
                DimensionScore instructionClarity,
                DimensionScore contextCompleteness,
                DimensionScore outputSpecification,
                DimensionScore constraintStrength,
                DimensionScore curriculumAlignment,
                DimensionScore pedagogicalQuality,
                Map<String, List<String>> weaknesses,
                CurriculumContext detectedContext) {
}