package SEP490.EduPrompt.dto.request.collection;

import SEP490.EduPrompt.dto.request.AddTagRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
public record CreateCollectionRequest(

        @NotBlank(message = "Name is required") @Size(max = 255)
        String name,

        String description,

        @NotNull @Pattern(regexp = "(private|public|school|group)", message = "Invalid visibility")
        String visibility,

        List<AddTagRequest> tags,

        UUID groupId
) {
}
