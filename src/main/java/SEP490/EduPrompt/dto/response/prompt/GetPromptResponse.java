package SEP490.EduPrompt.dto.response.prompt;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class GetPromptResponse {
    private String title;
    private String description;
    private String outputFormat;
    private String visibility;
    private String fullName;
    private String collectionName;
    private Instant createdAt;
    private Instant updatedAt;
}
