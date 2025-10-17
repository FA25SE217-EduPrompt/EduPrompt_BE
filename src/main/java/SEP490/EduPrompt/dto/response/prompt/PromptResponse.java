package SEP490.EduPrompt.dto.response.prompt;

import SEP490.EduPrompt.enums.Visibility;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class PromptResponse {
    private UUID id;
    private String title;
    private String description;
    private String instruction;
    private String context;
    private String inputExample;
    private String outputFormat;
    private String constraints;
    private String visibility;
    private UUID userId;
    private UUID collectionId;
    private List<TagDTO> tags;
    private Instant createdAt;
    private Instant updatedAt;
}


