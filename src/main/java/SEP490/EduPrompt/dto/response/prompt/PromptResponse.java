package SEP490.EduPrompt.dto.response.prompt;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class PromptResponse {
    private UUID id;
    private String title;
    private String description;
    private String outputFormat;
    private String visibility;
    private String fullName; // fullName of the user who created the prompt
    private String collectionName;
    private Instant createdAt;
    private Instant updatedAt;
}
