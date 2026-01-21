package SEP490.EduPrompt.dto.response.user;

import java.util.UUID;

public record UserSchoolResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String role,
        Boolean isActive
) {
}
