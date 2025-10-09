package SEP490.EduPrompt.dto.request.collection;

import SEP490.EduPrompt.model.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpdateCollectionRequest(
        @Size(max = 255) String name,
        String description,
        @Pattern(regexp = "(private|public|school|group)", message = "Invalid visibility") String visibility,
        List<Tag> tags,
        UUID groupId  // Required if visibility='group'
) {
}
