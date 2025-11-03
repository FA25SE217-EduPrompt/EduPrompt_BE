package SEP490.EduPrompt.dto.request.prompt;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SaveOptimizedPromptRequest(
        @NotNull(message = "Queue ID is required")
        UUID queueId
) {}
