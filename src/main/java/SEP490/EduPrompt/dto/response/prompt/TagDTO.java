package SEP490.EduPrompt.dto.response.prompt;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class TagDTO {
    private UUID id;
    private String type;
    private String value;
}
