package SEP490.EduPrompt.dto.request.prompt;

import java.util.List;
import java.util.UUID;

public record PromptFilterRequest(
        UUID createdBy,
        String collectionName,
        List<String> tagTypes,
        List<String> tagValues,
        String schoolName,
        String groupName,
        String title,
        Boolean includeDeleted
) {
}
