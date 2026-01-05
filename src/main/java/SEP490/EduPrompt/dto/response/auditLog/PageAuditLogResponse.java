package SEP490.EduPrompt.dto.response.auditLog;

import lombok.Builder;

import java.util.List;

@Builder
public record PageAuditLogResponse(
        List<AuditLogResponse> content,
        long totalElements,
        int totalPages,
        int pageNumber,
        int pageSize
) {
}
