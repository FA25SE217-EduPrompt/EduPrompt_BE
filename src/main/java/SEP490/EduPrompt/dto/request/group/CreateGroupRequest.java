package SEP490.EduPrompt.dto.request.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateGroupRequest(
        @NotBlank
        @Size(min = 1, max = 255)
        String name
) {
}
