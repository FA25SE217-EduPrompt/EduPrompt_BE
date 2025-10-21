package SEP490.EduPrompt.dto.response.prompt;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class DetailPromptResponse {

    private String title;
    private String description;
    private String instruction;
    private String context;
    private String inputExample;
    private String outputFormat;
    private String constraints;
    private String visibility;
    private String fullName;
    private String collectionName;
    private List<TagDTO> tags;
    private Instant createdAt;
    private Instant updatedAt;
}


