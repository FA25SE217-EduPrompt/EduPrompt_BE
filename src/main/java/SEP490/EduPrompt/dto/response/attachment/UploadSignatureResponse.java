package SEP490.EduPrompt.dto.response.attachment;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UploadSignatureResponse {
    private String signature;
    private Long timestamp;
    private String apiKey;
    private String cloudName;
    private String uploadPreset;
    private String resourceType;
}