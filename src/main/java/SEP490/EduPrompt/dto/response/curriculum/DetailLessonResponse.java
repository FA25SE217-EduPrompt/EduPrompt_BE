package SEP490.EduPrompt.dto.response.curriculum;

import lombok.Builder;

import java.util.UUID;

@Builder
public record DetailLessonResponse(
        UUID id,
        UUID chapterId,
        Integer lessonNumber,
        String name,
        String description,
        String content
) {}
