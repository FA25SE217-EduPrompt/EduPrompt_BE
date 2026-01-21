package SEP490.EduPrompt.dto.response.prompt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PromptScoreResponse(
        UUID id,
        String title,
        BigDecimal overallScore,
        Instant createdAt,
        Instant updatedAt
) {
}
