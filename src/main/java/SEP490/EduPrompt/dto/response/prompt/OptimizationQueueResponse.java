package SEP490.EduPrompt.dto.response.prompt;

import SEP490.EduPrompt.enums.QueueStatus;

import java.time.Instant;
import java.util.UUID;

public record OptimizationQueueResponse(
        UUID id,
        UUID promptId,
        QueueStatus status,
        String output,
        String errorMessage,
        int retryCount,
        int maxRetries,
        Instant createdAt,
        Instant updatedAt
) {
}
