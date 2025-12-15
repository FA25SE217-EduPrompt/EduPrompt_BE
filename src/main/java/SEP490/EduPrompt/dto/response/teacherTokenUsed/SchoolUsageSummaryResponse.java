package SEP490.EduPrompt.dto.response.teacherTokenUsed;

import java.time.Instant;
import java.util.List;

public record SchoolUsageSummaryResponse(
        String schoolName,
        Integer totalTeachers,
        Integer schoolTokenPool,
        Integer schoolTokenUsed,
        Integer schoolTokenRemaining,
        Instant quotaResetDate,
        List<TeacherUsageResponse> users
) {}
