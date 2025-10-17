package SEP490.EduPrompt.dto.request.collection;

import SEP490.EduPrompt.dto.request.AddTagRequest;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

//@Getter
public record UpdateCollectionRequest(
        @Size(max = 255) String name,
        String description,

        @Pattern(regexp = "(private|public|school|group)", message = "Invalid visibility")
        String visibility,

        List<AddTagRequest> tags,

        UUID groupId  // Required if visibility='group'
) {
}
