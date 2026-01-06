package SEP490.EduPrompt.dto.response.curriculum;

import lombok.Builder;

import java.util.UUID;

@Builder
public record LessonSuggestion(
        UUID lessonId,
        String lessonName,
        Double confidence,
        String reason
) {}
