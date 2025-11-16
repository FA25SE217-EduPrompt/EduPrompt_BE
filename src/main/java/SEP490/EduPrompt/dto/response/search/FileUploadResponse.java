package SEP490.EduPrompt.dto.response.search;

import lombok.Builder;

import java.util.UUID;

@Builder
public record FileUploadResponse(
        String fileId,
        String status, // PROCESSING, ACTIVE, FAILED
        UUID promptId
) {
}
