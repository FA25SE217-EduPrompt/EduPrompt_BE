package SEP490.EduPrompt.dto.response.search;

import lombok.Builder;

@Builder
public record GroundingChunk(
        String documentId,
        String text,
        Double confidenceScore,
        Integer startIndex,
        Integer endIndex
) {
}

