package SEP490.EduPrompt.service.curriculum;

import SEP490.EduPrompt.dto.response.curriculum.*;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.*;
import SEP490.EduPrompt.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CurriculumServiceImpl implements CurriculumService {
    private final LessonRepository lessonRepository;
    private final PromptRepository promptRepository;
    private final SubjectRepository subjectRepository;
    private final GradeLevelRepository gradeLevelRepository;
    private final SemesterRepository semesterRepository;
    private final ChapterRepository chapterRepository;

    @Override
    public DetailLessonResponse getLessonById(UUID id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found with id: " + id));
        return DetailLessonResponse.builder()
                .id(lesson.getId())
                .chapterId(lesson.getChapterId())
                .name(lesson.getName())
                .description(lesson.getDescription())
                .lessonNumber(lesson.getLessonNumber())
                .content(lesson.getContent())
                .build();
    }

    @Override
    public List<PromptLessonResponse> getPromptsByLessonId(UUID lessonId) {
        List<Prompt> prompts = promptRepository.findByLessonId(lessonId);
        return prompts.stream()
                .map(prompt -> PromptLessonResponse.builder()
                        .id(prompt.getId())
                        .userId(prompt.getUserId())
                        .collectionId(prompt.getCollectionId())
                        .title(prompt.getTitle())
                        .description(prompt.getDescription())
                        .instruction(prompt.getInstruction())
                        .context(prompt.getContext())
                        .inputExample(prompt.getInputExample())
                        .outputFormat(prompt.getOutputFormat())
                        .constraints(prompt.getConstraints())
                        .visibility(prompt.getVisibility())
                        .avgRating(prompt.getAvgRating())
                        .lessonId(prompt.getLessonId())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public CurriculumResponse getCurriculum(String subjectName, Integer gradeLevel, Integer semesterNumber) {
        if (subjectName == null || gradeLevel == null) {
            throw new ResourceNotFoundException("Subject name and grade level are required");
        }

        Subject subject = subjectRepository.findByNameIgnoreCase(subjectName);
        if (subject == null) {
            throw new ResourceNotFoundException("Subject not found with name: " + subjectName);
        }

        GradeLevel gl = gradeLevelRepository.findByLevelAndSubject(gradeLevel, subject);
        if (gl == null) {
            throw new ResourceNotFoundException("Grade level not found with level: " + gradeLevel);
        }

        List<Semester> semesters;
        if (semesterNumber != null) {
            Semester specificSemester = semesterRepository.findByGradeLevelAndSemesterNumber(gl, semesterNumber);
            if (specificSemester == null) {
                throw new ResourceNotFoundException("Semester " + semesterNumber + " not found for grade level " + gradeLevel);
            }
            semesters = List.of(specificSemester);
        } else {
            semesters = semesterRepository.findByGradeLevel(gl);
        }

        List<Chapter> chapters = !semesters.isEmpty()
                ? chapterRepository.findBySemesterIn(semesters)
                : Collections.emptyList();

        // Fetch lessons
        List<Lesson> lessons = !chapters.isEmpty()
                ? lessonRepository.findByChapterIn(chapters)
                : Collections.emptyList();

        Map<UUID, List<Lesson>> lessonsByChapterId = lessons.stream()
                .collect(Collectors.groupingBy(Lesson::getChapterId));

        Map<UUID, List<Chapter>> chaptersBySemesterId = chapters.stream()
                .collect(Collectors.groupingBy(Chapter::getSemesterId));

        // Build nested response
        List<SemesterResponse> semesterResponses = semesters.stream()
                .map(s -> {
                    List<Chapter> sChapters = chaptersBySemesterId.getOrDefault(s.getId(), Collections.emptyList());
                    List<ChapterResponse> chapterResponses = sChapters.stream()
                            .map(c -> toChapterResponse(c, lessonsByChapterId.getOrDefault(c.getId(), Collections.emptyList()).stream()
                                    .map(this::toLessonResponse)
                                    .collect(Collectors.toList())))
                            .collect(Collectors.toList());
                    return toSemesterResponse(s, chapterResponses);
                })
                .collect(Collectors.toList());

        return new CurriculumResponse(
                List.of(toSubjectResponse(subject)),
                List.of(toGradeLevelResponse(gl)),
                semesterResponses
        );
    }

    private SubjectResponse toSubjectResponse(Subject subject) {
        return SubjectResponse.builder()
                .id(subject.getId())
                .name(subject.getName())
                .description(subject.getDescription())
                .build();
    }

    private GradeLevelResponse toGradeLevelResponse(GradeLevel gradeLevel) {
        return GradeLevelResponse.builder()
                .id(gradeLevel.getId())
                .level(gradeLevel.getLevel())
                .description(gradeLevel.getDescription())
                .build();
    }

    private SemesterResponse toSemesterResponse(Semester semester, List<ChapterResponse> chapterResponses) {
        return SemesterResponse.builder()
                .id(semester.getId())
                .name(semester.getName())
                .semesterNumber(semester.getSemesterNumber())
                .listOfChapter(chapterResponses)
                .build();
    }

    private ChapterResponse toChapterResponse(Chapter chapter, List<LessonResponse> lessonResponses) {
        return ChapterResponse.builder()
                .id(chapter.getId())
                .semesterId(chapter.getSemesterId())
                .chapterNumber(chapter.getChapterNumber())
                .name(chapter.getName())
                .description(chapter.getDescription())
                .listOfLesson(lessonResponses)
                .build();
    }

    private LessonResponse toLessonResponse(Lesson lesson) {
        return LessonResponse.builder()
                .id(lesson.getId())
                .chapterId(lesson.getChapterId())
                .lessonNumber(lesson.getLessonNumber())
                .name(lesson.getName())
                .description(lesson.getDescription())
                .content(lesson.getContent())
                .build();
    }
}
