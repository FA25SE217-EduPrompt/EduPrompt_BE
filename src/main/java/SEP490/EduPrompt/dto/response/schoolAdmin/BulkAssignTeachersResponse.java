package SEP490.EduPrompt.dto.response.schoolAdmin;

import java.util.List;
import java.util.UUID;

public record BulkAssignTeachersResponse(
        int totalRequested,
        int assignedCount,                    // → emails added to list
        int createdCount,                     // → always 0
        List<String> skipped,                 // → with reasons
        List<String> newlyAddedEmails         // → useful for frontend
) {}