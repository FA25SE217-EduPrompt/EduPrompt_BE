package SEP490.EduPrompt.dto.request.prompt;

import java.util.List;
import java.util.UUID;

public record PromptFilterRequest(
        UUID createdBy,
        String collectionName,
        List<String> tagTypes,
        String schoolName,
        String groupName,
        String title,
        Boolean includeDeleted
) {
}
