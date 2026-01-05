package SEP490.EduPrompt.service.curriculum;

import SEP490.EduPrompt.dto.response.curriculum.CurriculumResponse;
import SEP490.EduPrompt.dto.response.curriculum.DetailLessonResponse;
import SEP490.EduPrompt.dto.response.curriculum.PromptLessonResponse;

import java.util.List;
import java.util.UUID;

public interface CurriculumService {
    DetailLessonResponse getLessonById(UUID id);
    List<PromptLessonResponse> getPromptsByLessonId(UUID lessonId);
    CurriculumResponse getCurriculum(String subjectName, Integer gradeLevel, Integer semesterNumber);
}
