package SEP490.EduPrompt.dto.response.schoolAdmin;

import SEP490.EduPrompt.model.User;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record BulkAssignTeachersResponse(
        List<UUID> userIds,
        int totalRequested,
        int assigned,
        int created,
        List<String> skipped,
        List<String> errors
) {}