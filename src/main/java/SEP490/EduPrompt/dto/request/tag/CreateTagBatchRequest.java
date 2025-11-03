package SEP490.EduPrompt.dto.request.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record CreateTagBatchRequest(
        @NotEmpty(message = "At least one tag must be provided")
        List<CreateTagRequest> tags
) {
    public record CreateTagRequest(
            @NotBlank @Size(max = 50)  String type,
            @NotBlank @Size(max = 100) String value
    ) {}
}
