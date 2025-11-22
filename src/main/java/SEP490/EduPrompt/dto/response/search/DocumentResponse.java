package SEP490.EduPrompt.dto.response.search;

import lombok.Builder;

import java.time.Instant;

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
