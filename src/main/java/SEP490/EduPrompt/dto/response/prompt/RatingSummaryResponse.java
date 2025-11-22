package SEP490.EduPrompt.dto.response.prompt;

import lombok.Builder;

@Builder
public record RatingSummaryResponse(
        Double avg,
        Long totalRating
) {
}
