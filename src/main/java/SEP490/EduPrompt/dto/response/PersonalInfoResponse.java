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
        UUID schoolId,
        boolean isVerified,
        boolean isActive,
        boolean isFreeTier,
        boolean isProTier,
        boolean isPremiumTier,
        boolean hasSchoolSubscription,
        boolean isSystemAdmin,
        boolean isSchoolAdmin,
        boolean isTeacher
) {
}
