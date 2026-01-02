package SEP490.EduPrompt.dto.response.curriculum;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record LessonResponse(
        UUID id,
        UUID chapterId,
        Integer lessonNumber,
        String name,
        String description
) {}
