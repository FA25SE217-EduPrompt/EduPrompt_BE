package SEP490.EduPrompt.dto.response.teacherProfile;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record TeacherProfileResponse(
        UUID id,
        String subjectSpecialty,
        String gradeLevels,
        String teachingStyle,
        Instant createdAt,
        Instant updatedAt
) {}
