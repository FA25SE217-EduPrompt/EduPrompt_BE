package SEP490.EduPrompt.dto.request.prompt;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UpdatePromptMetadataRequest {
    @NotBlank(message = "Title must not be blank")
    private String title;

    private String description;
    private String instruction;
    private String context;
    private String inputExample;
    private String outputFormat;
    private String constraints;
    private List<UUID> tagIds; // Optional list of tag IDs to associate
}
