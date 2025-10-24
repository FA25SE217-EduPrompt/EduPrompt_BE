package SEP490.EduPrompt.dto.response.prompt;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PaginatedDetailPromptResponse {
    private List<DetailPromptResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}