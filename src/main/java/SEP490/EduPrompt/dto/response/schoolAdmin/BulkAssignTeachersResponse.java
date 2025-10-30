package SEP490.EduPrompt.dto.response.schoolAdmin;

import java.util.List;

public record BulkAssignTeachersResponse(
        int totalRequested,
        int assigned,
        int created,
        List<String> skipped,
        List<String> errors
) {}