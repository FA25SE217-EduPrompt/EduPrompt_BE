package SEP490.EduPrompt.dto.request.collection;


import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

//@Getter
public record UpdateCollectionRequest(
        @Size(max = 255) String name,
        String description,

        @Pattern(regexp = "(private|public|school|group)", message = "Invalid visibility")
        String visibility,

        List<UUID> tags,

        UUID groupId  // Required if visibility='group'
) {
}
