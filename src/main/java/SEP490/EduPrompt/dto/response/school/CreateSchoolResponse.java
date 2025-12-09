package SEP490.EduPrompt.dto.response.school;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record CreateSchoolResponse(
        UUID id,
        String name,
        String address,
        String phoneNumber,
        String district,
        String province,
        Instant createdAt,
        Instant updatedAt
) {
}
