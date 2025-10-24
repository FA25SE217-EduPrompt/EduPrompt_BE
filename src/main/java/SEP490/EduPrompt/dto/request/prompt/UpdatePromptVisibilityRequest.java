package SEP490.EduPrompt.dto.request.prompt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UpdatePromptVisibilityRequest {
    @NotBlank(message = "Visibility must not be blank")
    @Pattern(regexp = "^(private|public|school|group)$", message = "Visibility must be one of: private, public, school, group")
    private String visibility; // e.g., "PRIVATE", "PUBLIC", "SCHOOL", "GROUP"

    private UUID collectionId; // Optional: For moving standalone prompts to a collection
}
