package SEP490.EduPrompt.dto.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record PersonalInfoResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        boolean isVerified,
        boolean isActive,
        boolean isSystemAdmin,
        boolean isSchoolAdmin,
        boolean isTeacher
) {
}
