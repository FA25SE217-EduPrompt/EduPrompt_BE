package SEP490.EduPrompt.dto.request.teacherProfile;

import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record UpdateTeacherProfileRequest(
        @Size(max = 500, message = "Subject specialty cannot exceed 500 characters")
        String subjectSpecialty,

        @Size(max = 200, message = "Grade levels cannot exceed 200 characters")
        String gradeLevels,

        @Size(max = 1000, message = "Teaching style cannot exceed 1000 characters")
        String teachingStyle
) {}
