package SEP490.EduPrompt.dto.response.search;

import lombok.Builder;

import java.util.UUID;

@Builder
public record IndexingResult(
        UUID promptId,
        String status, // success, failed, skipped
        String documentId,
        String errorMessage
) {
}
