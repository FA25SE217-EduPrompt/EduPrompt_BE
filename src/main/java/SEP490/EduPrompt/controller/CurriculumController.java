package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.curriculum.*;
import SEP490.EduPrompt.service.curriculum.CurriculumMatchingService;
import SEP490.EduPrompt.service.curriculum.CurriculumService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/curriculum")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
public class CurriculumController {

    private final CurriculumService curriculumService;
    private final CurriculumMatchingService curriculumMatchingService;

    @GetMapping("/lesson/{id}")
    public ResponseDto<DetailLessonResponse> getLesson(@PathVariable UUID id) {
        DetailLessonResponse detailLessonResponse = curriculumService.getLessonById(id);
        return ResponseDto.success(detailLessonResponse);
    }

    @GetMapping("/prompt/lesson/{lessonId}")
    public ResponseDto<List<PromptLessonResponse>> getPromptsByLesson(@PathVariable UUID lessonId) {
        List<PromptLessonResponse> promptResponses = curriculumService.getPromptsByLessonId(lessonId);
        return ResponseDto.success(promptResponses);
    }

    @GetMapping("/filters")
    public ResponseDto<CurriculumResponse> getCurriculum(
            @RequestParam String subjectName,
            @RequestParam Integer gradeLevel,
            @RequestParam(required = false) Integer semesterNumber) {
        CurriculumResponse response = curriculumService.getCurriculum(subjectName, gradeLevel, semesterNumber);
        return ResponseDto.success(response);
    }

    // NEW ENDPOINTS FOR OPTIMIZATION MODULE

    /**
     * POST /api/curriculum/detect-context
     * Detect curriculum context (subject, grade, lesson) from prompt text
     */
    @PostMapping("/detect-context")
    public ResponseDto<CurriculumContext> detectContext(
            @RequestBody String promptText) {

        log.info("Detecting curriculum context from prompt text");
        CurriculumContext context = curriculumMatchingService.detectContext(promptText);
        return ResponseDto.success(context);
    }

    /**
     * GET /api/curriculum/lesson/{lessonId}/detail
     * Get full curriculum context detail for a lesson (for optimization)
     */
    @GetMapping("/lesson/{lessonId}/detail")
    public ResponseDto<CurriculumContextDetail> getLessonContextDetail(@PathVariable UUID lessonId) {

        log.info("Fetching curriculum context detail for lesson: {}", lessonId);
        CurriculumContextDetail detail = curriculumMatchingService.getContextDetail(lessonId);
        return ResponseDto.success(detail);
    }

    /**
     * POST /api/curriculum/lessons/search
     * Search lessons by keyword with optional subject and grade filters
     */
    @PostMapping("/lessons/search")
    public ResponseDto<List<LessonSearchResult>> searchLessons(
            @RequestParam String keyword,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) Integer gradeLevel) {

        log.info("Searching lessons: keyword={}, subject={}, grade={}", keyword, subjectId, gradeLevel);
        List<LessonSearchResult> results = curriculumMatchingService.searchLessons(keyword, subjectId, gradeLevel);
        return ResponseDto.success(results);
    }

    /**
     * POST /api/curriculum/lesson/suggest
     * Suggest a lesson based on prompt text analysis
     */
    @PostMapping("/lesson/suggest")
    public ResponseDto<LessonSuggestion> suggestLesson(
            @RequestParam String promptText,
            @RequestParam UUID subjectId,
            @RequestParam Integer gradeLevel) {

        log.info("Suggesting lesson for prompt");
        LessonSuggestion suggestion = curriculumMatchingService.suggestLesson(promptText, subjectId, gradeLevel);

        if (suggestion == null) {
            return ResponseDto.success(null);
        }

        return ResponseDto.success(suggestion);
    }
}



