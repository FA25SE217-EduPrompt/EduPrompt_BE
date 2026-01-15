package SEP490.EduPrompt.curriculum;

import SEP490.EduPrompt.dto.response.curriculum.*;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.*;
import SEP490.EduPrompt.repo.*;
import SEP490.EduPrompt.service.curriculum.CurriculumServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurriculumServiceImplTest {

    @Mock private LessonRepository lessonRepository;
    @Mock private PromptRepository promptRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private GradeLevelRepository gradeLevelRepository;
    @Mock private SemesterRepository semesterRepository;
    @Mock private ChapterRepository chapterRepository;

    @InjectMocks
    private CurriculumServiceImpl curriculumService;

    private UUID lessonId;
    private Lesson lesson;

    @BeforeEach
    void setUp() {
        lessonId = UUID.randomUUID();
        lesson = Lesson.builder()
                .id(lessonId)
                .chapterId(UUID.randomUUID())
                .name("Introduction")
                .description("Basic concepts")
                .content("Content here")
                .lessonNumber(1)
                .build();
    }

    // ======================================================================//
    // ======================== GET LESSON BY ID ============================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Get Lesson - Success")
    void getLessonById_WhenFound_ShouldReturnDetail() {
        // Arrange
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));

        // Act
        DetailLessonResponse response = curriculumService.getLessonById(lessonId);

        // Assert
        assertNotNull(response);
        assertEquals(lessonId, response.id());
        assertEquals("Introduction", response.name());
        assertEquals("Content here", response.content());
    }

    @Test
    @DisplayName("Case 2: Get Lesson - Fail (Not Found)")
    void getLessonById_WhenNotFound_ShouldThrowException() {
        // Arrange
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> curriculumService.getLessonById(lessonId));
    }

    // ======================================================================//
    // ===================== GET PROMPTS BY LESSON ID =======================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Get Prompts - Success (Returns List)")
    void getPromptsByLessonId_WhenPromptsExist_ShouldReturnMappedList() {
        // Arrange
        Prompt prompt = Prompt.builder()
                .id(UUID.randomUUID())
                .title("Math Prompt")
                .lessonId(lessonId)
                .build();

        when(promptRepository.findByLessonId(lessonId)).thenReturn(List.of(prompt));

        // Act
        List<PromptLessonResponse> response = curriculumService.getPromptsByLessonId(lessonId);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals("Math Prompt", response.get(0).title());
    }

    @Test
    @DisplayName("Case 2: Get Prompts - Success (Empty)")
    void getPromptsByLessonId_WhenNoneExist_ShouldReturnEmptyList() {
        // Arrange
        when(promptRepository.findByLessonId(lessonId)).thenReturn(Collections.emptyList());

        // Act
        List<PromptLessonResponse> response = curriculumService.getPromptsByLessonId(lessonId);

        // Assert
        assertNotNull(response);
        assertTrue(response.isEmpty());
    }

    // ======================================================================//
    // ========================= GET CURRICULUM =============================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Get Curriculum - Success (Full Hierarchy)")
    void getCurriculum_WhenValidInputs_ShouldReturnTreeStructure() {
        // Arrange
        String subjectName = "Math";
        int grade = 10;

        Subject subject = Subject.builder().id(UUID.randomUUID()).name(subjectName).build();
        GradeLevel gradeLevel = GradeLevel.builder().id(UUID.randomUUID()).level(grade).build();
        Semester semester = Semester.builder().id(UUID.randomUUID()).semesterNumber(1).build();
        Chapter chapter = Chapter.builder().id(UUID.randomUUID()).semesterId(semester.getId()).name("Calculus").build();

        // Link lesson to chapter
        lesson.setChapterId(chapter.getId());

        // Mocks for Hierarchy Traversal
        when(subjectRepository.findByNameIgnoreCase(subjectName)).thenReturn(Optional.of(subject));
        when(gradeLevelRepository.findByLevelAndSubject(grade, subject)).thenReturn(gradeLevel);

        // Fetch all semesters
        when(semesterRepository.findByGradeLevel(gradeLevel)).thenReturn(List.of(semester));

        // Fetch chapters for semesters
        when(chapterRepository.findBySemesterIn(anyList())).thenReturn(List.of(chapter));

        // Fetch lessons for chapters
        when(lessonRepository.findByChapterIn(anyList())).thenReturn(List.of(lesson));

        // Act
        CurriculumResponse response = curriculumService.getCurriculum(subjectName, grade, null);

        // Assert
        assertNotNull(response);
        // Verify Subject
        assertEquals(1, response.subjects().size());
        assertEquals("Math", response.subjects().get(0).name());

        // Verify Grade
        assertEquals(1, response.gradeLevels().size());
        assertEquals(10, response.gradeLevels().get(0).level());

        // Verify Nested Structure (Semester -> Chapter -> Lesson)
        assertEquals(1, response.semesters().size()); // Semesters
        assertEquals(1, response.semesters().get(0).listOfChapter().size()); // Chapters
        assertEquals(1, response.semesters().get(0).listOfChapter().get(0).listOfLesson().size()); // Lessons

        assertEquals("Introduction", response.semesters().get(0).listOfChapter().get(0).listOfLesson().get(0).name());
    }

    @Test
    @DisplayName("Case 2: Get Curriculum - Fail (Subject Not Found)")
    void getCurriculum_WhenSubjectMissing_ShouldThrowResourceNotFound() {
        // Arrange
        when(subjectRepository.findByNameIgnoreCase("Unknown")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> curriculumService.getCurriculum("Unknown", 10, null));
    }

    @Test
    @DisplayName("Case 3: Get Curriculum - Fail (Grade Level Not Found)")
    void getCurriculum_WhenGradeLevelMissing_ShouldThrowResourceNotFound() {
        // Arrange
        Subject subject = Subject.builder().id(UUID.randomUUID()).name("Math").build();

        when(subjectRepository.findByNameIgnoreCase("Math")).thenReturn(Optional.of(subject));
        when(gradeLevelRepository.findByLevelAndSubject(99, subject)).thenReturn(null);

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> curriculumService.getCurriculum("Math", 99, null));
    }

    @Test
    @DisplayName("Case 4: Get Curriculum - Success (Specific Semester Not Found)")
    void getCurriculum_WhenSpecificSemesterMissing_ShouldThrowException() {
        // Arrange
        Subject subject = Subject.builder().name("Math").build();
        GradeLevel gradeLevel = GradeLevel.builder().level(10).build();

        when(subjectRepository.findByNameIgnoreCase("Math")).thenReturn(Optional.of(subject));
        when(gradeLevelRepository.findByLevelAndSubject(10, subject)).thenReturn(gradeLevel);
        // Semester 5 does not exist
        when(semesterRepository.findByGradeLevelAndSemesterNumber(gradeLevel, 5)).thenReturn(null);

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> curriculumService.getCurriculum("Math", 10, 5));
    }
}
