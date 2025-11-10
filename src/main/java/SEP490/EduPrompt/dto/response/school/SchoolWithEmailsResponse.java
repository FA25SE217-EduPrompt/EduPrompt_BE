package SEP490.EduPrompt.dto.response.school;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record SchoolWithEmailsResponse(
        UUID id,
        String name,
        String address,
        String phoneNumber,
        String district,
        String province,
        Instant createdAt,
        Instant updatedAt,
        Set<SchoolEmailResponse> emails
) {}
