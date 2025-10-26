package SEP490.EduPrompt.dto.response.attachment;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Builder
@Getter
public class AttachmentResponse {
    private UUID id;
    private String url;
    private String publicId;
    private String fileName;
    private String fileType;
    private Long size;
    private Instant createdAt;
}