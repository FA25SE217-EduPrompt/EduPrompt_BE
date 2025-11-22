package SEP490.EduPrompt.dto.request.tag;

import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record AddTagsToPromptRequest(
        @NotEmpty(message = "tagIds must not be empty")
        List<UUID> tagIds
) {
}