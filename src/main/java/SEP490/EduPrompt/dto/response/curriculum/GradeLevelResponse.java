package SEP490.EduPrompt.dto.response.curriculum;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record GradeLevelResponse(
        UUID id,
        Integer level,
        String description
) {}
