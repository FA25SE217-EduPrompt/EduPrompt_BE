package SEP490.EduPrompt.dto.response.curriculum;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record SubjectResponse(
        UUID id,
        String name,
        String description
) {}