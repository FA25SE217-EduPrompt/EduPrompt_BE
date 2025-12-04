package SEP490.EduPrompt.dto.response.prompt;

import lombok.Builder;

import java.util.List;

@Builder
public record PagePromptResponse(
        List<PromptSummaryResponse> content,
        long totalElements,
        int totalPages,
        int pageNumber,
        int pageSize
) {
}