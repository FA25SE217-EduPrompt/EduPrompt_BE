package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.curriculum.CurriculumResponse;
import SEP490.EduPrompt.dto.response.curriculum.DetailLessonResponse;
import SEP490.EduPrompt.dto.response.curriculum.PromptLessonResponse;
import SEP490.EduPrompt.service.curriculum.CurriculumService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/curriculum")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
public class CurriculumController {
    private final CurriculumService curriculumService;

    @GetMapping("/lesson/{id}")
    public ResponseDto<DetailLessonResponse> getLesson(
            @PathVariable UUID id) {
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
}
