package SEP490.EduPrompt.dto.response.auditLog;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record AuditLogResponse (
        UUID id,
        UUID userId,
        String actionLog,
        Instant createdAt
){
}
