package SEP490.EduPrompt.dto.request.attachment;

import SEP490.EduPrompt.enums.FileType;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class AttachmentRequest {

    @NotBlank(message = "URL is required")
    private String url;

    @NotBlank(message = "Public ID is required")
    private String publicId;

    private FileType fileType;

    private String fileName;

    private Long size;

}