package SEP490.EduPrompt.dto.response.prompt;

import SEP490.EduPrompt.model.Tag;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PromptSummaryResponse(
        UUID id,
        String title,
        String description,
        String ownerName,
        Double avgRating
) {
}
