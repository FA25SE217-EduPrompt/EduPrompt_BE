package SEP490.EduPrompt.dto.response.schoolAdmin;

import SEP490.EduPrompt.model.User;

import java.util.List;

public record BulkAssignTeachersResponse(
        List<User> users,
        int totalRequested,
        int assigned,
        int created,
        List<String> skipped,
        List<String> errors
) {}