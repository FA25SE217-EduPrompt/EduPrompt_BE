package SEP490.EduPrompt.dto.request.search;

import java.util.List;
import java.util.UUID;

public record SearchContext(
        List<String> tags,
        String currentPrompt,
        String visibility,
        UUID schoolId,
        UUID groupId
) {
}

