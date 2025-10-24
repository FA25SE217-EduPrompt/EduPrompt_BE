package SEP490.EduPrompt.dto.request.prompt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CreatePromptRequest {
    @NotBlank
    @Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
    private String title;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @Size(max = 5000, message = "Instruction must not exceed 5000 characters")
    private String instruction;

    @Size(max = 5000, message = "Context must not exceed 5000 characters")
    private String context;

    @Size(max = 2000, message = "Input example must not exceed 2000 characters")
    private String inputExample;

    @Size(max = 2000, message = "Output format must not exceed 2000 characters")
    private String outputFormat;

    @Size(max = 2000, message = "Constraints must not exceed 2000 characters")
    private String constraints;

    @NotNull(message = "Visibility is required")
    private String visibility;

    @Size(max = 10, message = "Maximum 10 tags allowed")
    private List<UUID> tagIds;
}
