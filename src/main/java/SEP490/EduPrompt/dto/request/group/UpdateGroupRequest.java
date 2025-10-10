package SEP490.EduPrompt.dto.request.group;

import SEP490.EduPrompt.model.GroupMember;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record UpdateGroupRequest(
        @NotBlank
        @Size(min = 1, max = 255)
        String name,
        @NotNull
        Boolean isActive
) {
}
