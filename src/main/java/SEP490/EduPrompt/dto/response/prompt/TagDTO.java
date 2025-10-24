package SEP490.EduPrompt.dto.response.prompt;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TagDTO {
    private String type;
    private String value;
}
