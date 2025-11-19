package SEP490.EduPrompt.dto.response.search;

import lombok.Builder;

import java.util.UUID;

@Builder
public record SearchResultItem(
        UUID promptId,
        String title,
        String description,
        Double relevanceScore,   // 0.0 -> 1.0
        String matchedSnippet,   //  relevant chunk from document
        String reasoning,
        String visibility,
        UUID createdBy,
        String createdByName,
        Double averageRating
) {
}
