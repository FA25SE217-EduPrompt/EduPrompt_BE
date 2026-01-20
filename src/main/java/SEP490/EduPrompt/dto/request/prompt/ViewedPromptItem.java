package SEP490.EduPrompt.dto.request.prompt;

import java.util.UUID;

public record ViewedPromptItem(
        UUID id,
        boolean value
) {
}