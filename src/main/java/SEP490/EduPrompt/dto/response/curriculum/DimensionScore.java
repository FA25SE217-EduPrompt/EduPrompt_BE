package SEP490.EduPrompt.dto.response.curriculum;

import lombok.Builder;

import java.util.List;

@Builder
public record DimensionScore(
        String dimensionName,
        Double score,
        Double maxScore,
        Double ruleBasedScore,
        Double aiAssistedScore,
        List<String> issues,
        List<String> suggestions
) {}
