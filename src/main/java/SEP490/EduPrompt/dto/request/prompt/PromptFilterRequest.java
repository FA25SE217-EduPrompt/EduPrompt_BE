package SEP490.EduPrompt.dto.request.prompt;

import java.util.List;
import java.util.UUID;

public record PromptFilterRequest(
        String visibility,
        UUID createdBy,
        String collectionName,
        List<String> tagTypes,
        String schoolName,
        String groupName,
        String searchText,
        Boolean includeDeleted
) {
}
