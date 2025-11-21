package SEP490.EduPrompt.dto.response.search;

import com.google.genai.JsonSerializable;
import lombok.Builder;

import java.time.Instant;
import java.util.Map;

@Builder
public record DocumentResponse(
        String name,
        String displayName,
        String state,
        Long sizeBytes,
        String mimeType,
        Instant createTime,
        Instant updateTime
) {
}
