package SEP490.EduPrompt.dto.response.curriculum;

import lombok.Builder;

import java.util.UUID;

@Builder
public record LessonSearchResult(
        UUID lessonId,
        String lessonName,
        String lessonContent,
        String chapterName,
        String subjectName,
        Integer gradeLevel,
        Double relevanceScore
) {
}
