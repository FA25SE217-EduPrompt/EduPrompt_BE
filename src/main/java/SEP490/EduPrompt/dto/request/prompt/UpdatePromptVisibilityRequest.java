package SEP490.EduPrompt.dto.request.prompt;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UpdatePromptVisibilityRequest {
    @NotBlank(message = "Visibility must not be blank")
    private String visibility; // e.g., "PRIVATE", "PUBLIC", "SCHOOL", "GROUP"

    private UUID collectionId; // Optional: For moving standalone prompts to a collection
}
