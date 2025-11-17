package SEP490.EduPrompt.dto.response.search;

import lombok.Builder;

import java.util.UUID;

@Builder
public record FileUploadResponse(
        String fileId,
        String documentId,
        String operationId,
        String status, // PROCESSING, ACTIVE, FAILED
        UUID promptId
) {
}
