package SEP490.EduPrompt.dto.request.prompt;

import java.util.List;

public record PagePromptResponse(
        List<PromptSummaryResponse> content,
        long totalElements,
        int totalPages,
        int pageNumber,
        int pageSize
) {}