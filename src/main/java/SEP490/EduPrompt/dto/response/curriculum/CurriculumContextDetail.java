package SEP490.EduPrompt.dto.response.curriculum;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CurriculumContextDetail(
        UUID lessonId,
        String lessonName,
        String lessonContent,
        Integer lessonNumber,
        String chapterName,
        Integer chapterNumber,
        Integer semester,
        Integer gradeLevel,
        String subjectName
) {
}
