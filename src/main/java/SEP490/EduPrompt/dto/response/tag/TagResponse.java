package SEP490.EduPrompt.dto.response.tag;

import lombok.Builder;

import java.util.UUID;

@Builder
public record TagResponse(
        UUID id,
        String type,
        String value
) {}
