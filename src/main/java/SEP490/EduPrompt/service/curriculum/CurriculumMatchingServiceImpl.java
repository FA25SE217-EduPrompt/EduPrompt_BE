package SEP490.EduPrompt.service.curriculum;

import SEP490.EduPrompt.dto.response.curriculum.CurriculumContext;
import SEP490.EduPrompt.dto.response.curriculum.CurriculumContextDetail;
import SEP490.EduPrompt.dto.response.curriculum.LessonSearchResult;
import SEP490.EduPrompt.dto.response.curriculum.LessonSuggestion;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.*;
import SEP490.EduPrompt.repo.LessonRepository;
import SEP490.EduPrompt.repo.SubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CurriculumMatchingServiceImpl implements CurriculumMatchingService {

    private static final Map<String, List<String>> SUBJECT_PATTERNS = Map.of(
            "Toán", List.of("toán", "hình học", "đại số", "giải tích", "lượng giác", "math", "mathematics"),
            "Văn", List.of("văn", "ngữ văn", "văn học", "tiếng việt", "literature"),
            "Anh", List.of("tiếng anh", "anh văn", "english"),
            "Lý", List.of("vật lý", "vật lí", "physics"),
            "Hóa", List.of("hóa học", "hoá học", "chemistry"),
            "Sinh", List.of("sinh học", "sinh vật", "biology"),
            "Sử", List.of("lịch sử", "history"),
            "Địa", List.of("địa lý", "địa lí", "geography"));
    private static final Set<String> STOPWORDS = Set.of(
            "tạo", "viết", "thiết", "kế", "cho", "của", "và", "với", "một", "các",
            "bài", "giáo", "án", "học", "sinh", "lớp", "tiết");
    private final LessonRepository lessonRepository;
    private final SubjectRepository subjectRepository;

    @Override
    public CurriculumContext detectContext(String promptText) {
        log.info("Detecting curriculum context from prompt");

        String normalizedText = promptText.toLowerCase().trim();

        CurriculumContext context = CurriculumContext.builder()
                .subject(detectSubject(normalizedText))
                .gradeLevel(detectGradeLevel(normalizedText))
                .semester(detectSemester(normalizedText))
                .detectedKeywords(extractKeywords(normalizedText))
                .build();

        if (context.getSubject() != null) {
            subjectRepository.findByNameIgnoreCase(context.getSubject())
                    .ifPresent(subject -> context.setSubjectId(subject.getId()));
        }

        log.info("Detected context: subject={}, grade={}, semester={}",
                context.getSubject(), context.getGradeLevel(), context.getSemester());

        return context;
    }

    private String detectSubject(String text) {
        Map<String, Integer> scores = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : SUBJECT_PATTERNS.entrySet()) {
            for (String pattern : entry.getValue()) {
                if (text.contains(pattern)) {
                    // Weight matches: Exact subject name > derived words
                    // E.g. "hóa học" (7 chars) > "hóa" (3 chars)
                    int score = pattern.length();
                    scores.merge(entry.getKey(), score, Integer::sum);
                }
            }
        }

        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private Integer detectGradeLevel(String text) {
        // Matches "lớp 11", "lớp: 11", "khối 11", "khối: 11", "grade 11"
        Pattern gradePattern = Pattern.compile("(lớp|khối|grade)[^\\d]{0,5}(10|11|12)");
        Matcher matcher = gradePattern.matcher(text);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group(2));
        }
        return null;
    }

    private Integer detectSemester(String text) {
        // Matches "học kỳ 1", "học kỳ: 1", "hk1", "hk 1", "semester 1", "semester: 1"
        Pattern sem1Pattern = Pattern.compile("(học kỳ|hk|semester)[^\\d]{0,5}1");
        if (sem1Pattern.matcher(text).find()) {
            return 1;
        }

        Pattern sem2Pattern = Pattern.compile("(học kỳ|hk|semester)[^\\d]{0,5}2");
        if (sem2Pattern.matcher(text).find()) {
            return 2;
        }
        return null;
    }

    private List<String> extractKeywords(String text) {
        // Simple keyword extraction: words > 3 chars, not stopwords, not numbers
        return Arrays
                .stream(text.toLowerCase()
                        .split("[^a-záàảãạăằắẳẵặâầấẩẫậéèẻẽẹêềếểễệíìỉĩịóòỏõọôồốổỗộơờớởỡợúùủũụưừứửữựýỳỷỹỵđ]+"))
                .filter(w -> w.length() > 2)
                .filter(w -> !STOPWORDS.contains(w))
                .distinct()
                .limit(10)
                .collect(Collectors.toList());
    }

    @Override
    public CurriculumContextDetail getContextDetail(UUID lessonId) {
        Lesson lesson = lessonRepository.findByIdWithHierarchy(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("lesson not found with id: " + lessonId));

        Chapter chapter = lesson.getChapter();
        Semester semester = chapter.getSemester();
        GradeLevel gradeLevel = semester.getGradeLevel();
        Subject subject = gradeLevel.getSubject();

        return new CurriculumContextDetail(
                lesson.getId(),
                lesson.getName(),
                lesson.getContent(),
                lesson.getLessonNumber(),
                chapter.getName(),
                chapter.getChapterNumber(),
                semester.getSemesterNumber(),
                gradeLevel.getLevel(),
                subject.getName());
    }

    @Override
    public List<LessonSearchResult> searchLessons(String keyword, UUID subjectId, Integer gradeLevel) {
        log.info("Searching lessons: keyword={}, subject={}, grade={}", keyword, subjectId, gradeLevel);

        List<LessonRepository.LessonSearchResultProjection> projections = lessonRepository.searchByKeyword(
                keyword, subjectId, gradeLevel);

        return projections.stream()
                .map(p -> LessonSearchResult.builder()
                        .lessonId(p.getLessonId())
                        .lessonName(p.getLessonName())
                        .lessonContent(p.getLessonContent())
                        .chapterName(p.getChapterName())
                        .subjectName(p.getSubjectName())
                        .gradeLevel(p.getGradeLevel())
                        .relevanceScore(p.getRelevanceScore())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public LessonSuggestion suggestLesson(String promptText, UUID subjectId, Integer gradeLevel) {
        CurriculumContext context = detectContext(promptText);
        List<String> keywords = context.getDetectedKeywords();

        if (keywords.isEmpty()) {
            log.warn("No keywords detected for lesson suggestion");
            return null;
        }

        String searchQuery = String.join(" ", keywords);
        List<LessonSearchResult> results = searchLessons(searchQuery, subjectId, gradeLevel);

        if (results.isEmpty()) {
            log.warn("No lessons found matching keywords: {}", keywords);
            return null;
        }

        LessonSearchResult topMatch = results.get(0);

        return new LessonSuggestion(
                topMatch.lessonId(),
                topMatch.lessonName(),
                topMatch.relevanceScore(),
                "Matched keywords: " + String.join(", ", keywords.subList(0, Math.min(3, keywords.size()))));
    }
}