package SEP490.EduPrompt.dto.response.user;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record UserResponse(
        UUID id,
        UUID subscriptionTierId,
        UUID schoolId,
        String firstName,
        String lastName,
        String phoneNumber,
        String email,
        String role,
        Boolean isActive,
        Boolean isVerified,
        Instant createdAt,
        Instant updatedAt
) {

}
