package SEP490.EduPrompt.dto.response.schoolAdmin;

import lombok.Builder;
import java.time.Instant;
import java.util.UUID;

@Builder
public record SchoolAdminTeacherResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        boolean isActive,
        boolean isVerified,
        Instant createdAt
) {}
