package SEP490.EduPrompt.dto.response.prompt;

import lombok.Builder;

import java.util.List;

@Builder
public record PaginatedGroupSharedPromptResponse (
        List<GroupSharedPromptResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
){
}
