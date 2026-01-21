package SEP490.EduPrompt.dto.response.prompt;

import java.util.List;

public record PagePromptScoreResponse(
        List<PromptScoreResponse> content,
        long totalElements,
        int totalPages,
        int pageNumber,
        int pageSize
) {
}
