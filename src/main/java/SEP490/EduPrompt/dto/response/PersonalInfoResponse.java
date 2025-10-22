package SEP490.EduPrompt.dto.response;

import lombok.Builder;

@Builder
public record PersonalInfoResponse(
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        boolean isVerified,
        boolean isActive,
        boolean isSystemAdmin,
        boolean isSchoolAdmin,
        boolean isTeacher
        ){}
