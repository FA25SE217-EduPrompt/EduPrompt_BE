package SEP490.EduPrompt.service.curriculum;

import SEP490.EduPrompt.dto.response.curriculum.CurriculumContext;
import SEP490.EduPrompt.dto.response.curriculum.CurriculumContextDetail;
import SEP490.EduPrompt.dto.response.curriculum.LessonSearchResult;
import SEP490.EduPrompt.dto.response.curriculum.LessonSuggestion;

import java.util.List;
import java.util.UUID;

public interface CurriculumMatchingService {

    // detect curriculum context from prompt text
    CurriculumContext detectContext(String promptText);

    // get full curriculum context for scoring
    CurriculumContextDetail getContextDetail(UUID lessonId);

    // search lessons by keyword
    List<LessonSearchResult> searchLessons(String keyword, UUID subjectId, Integer gradeLevel);

    // suggest lesson if prompt doesnt specify one
    LessonSuggestion suggestLesson(String promptText, UUID subjectId, Integer gradeLevel);
}