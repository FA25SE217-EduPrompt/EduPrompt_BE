package SEP490.EduPrompt.dto.response.search;


import lombok.Builder;

@Builder
public record ImportOperationResponse(
        String operationName,
        Boolean done,
        String status, // pending, processing, completed, failed
        String documentId,
        String errorMessage
) {
}
