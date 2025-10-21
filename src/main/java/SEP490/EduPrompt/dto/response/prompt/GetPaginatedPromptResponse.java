package SEP490.EduPrompt.dto.response.prompt;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GetPaginatedPromptResponse
{
    private List<GetPromptResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
