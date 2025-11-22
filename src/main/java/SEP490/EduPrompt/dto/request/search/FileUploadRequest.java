package SEP490.EduPrompt.dto.request.search;

import lombok.Builder;

import java.io.InputStream;

@Builder
public record FileUploadRequest(
        InputStream file,
        String mimeType,
        String displayName
) {
}
