package SEP490.EduPrompt.dto.request.attachment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.UUID;

@Getter
public class AttachmentRequest {

    @NotBlank(message = "URL is required")
    private String url;

    @NotBlank(message = "Public ID is required")
    private String publicId;

    private String fileType;

    private String fileName;

    private Long size;

    @NotNull(message = "Prompt Version ID is required")
    private UUID promptVersionId;

}