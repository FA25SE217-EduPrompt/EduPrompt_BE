package SEP490.EduPrompt.dto.response.school;

import lombok.Builder;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Builder
public record SchoolResponse(
        UUID id,
        String name,
        String address,
        String phoneNumber,
        String district,
        String province,
        Instant createdAt,
        Instant updatedAt,
        Set<String> schoolEmails
) {
}
