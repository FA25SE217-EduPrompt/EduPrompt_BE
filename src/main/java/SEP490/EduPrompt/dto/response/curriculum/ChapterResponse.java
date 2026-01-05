package SEP490.EduPrompt.dto.response.curriculum;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record ChapterResponse(
        UUID id,
        UUID semesterId,
        Integer chapterNumber,
        String name,
        String description,
        List<LessonResponse> listOfLesson
) {
}
