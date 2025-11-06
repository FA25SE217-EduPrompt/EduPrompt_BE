package SEP490.EduPrompt.dto.response.schoolAdmin;

import java.util.List;
import java.util.UUID;

public record BulkAssignTeachersResponse(
        List<UUID> userIds,
        int totalRequested,
        int assigned,
        int created,
        List<String> skipped,
        List<String> errors
) {}