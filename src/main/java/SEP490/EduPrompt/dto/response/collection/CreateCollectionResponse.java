package SEP490.EduPrompt.dto.response.collection;

import SEP490.EduPrompt.dto.response.prompt.TagDTO;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
@Data
public class CreateCollectionResponse {
    private UUID id;
    private String name;
    private String description;
    private String visibility;
    private List<TagDTO> tags;
    private Instant createdAt;
}
