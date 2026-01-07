package SEP490.EduPrompt.service.prompt;

import SEP490.EduPrompt.dto.response.curriculum.CurriculumContext;
import SEP490.EduPrompt.dto.response.curriculum.CurriculumContextDetail;
import SEP490.EduPrompt.dto.response.curriculum.DimensionScore;
import SEP490.EduPrompt.dto.response.curriculum.LessonSuggestion;
import SEP490.EduPrompt.dto.response.prompt.PromptScoreResult;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.model.PromptScore;
import SEP490.EduPrompt.model.PromptVersion;
import SEP490.EduPrompt.repo.PromptRepository;
import SEP490.EduPrompt.repo.PromptScoreRepository;
import SEP490.EduPrompt.repo.PromptVersionRepository;
import SEP490.EduPrompt.service.ai.AiClientServiceImpl;
import SEP490.EduPrompt.service.curriculum.CurriculumMatchingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class PromptScoringServiceImpl implements PromptScoringService {

    private final CurriculumMatchingService curriculumService;
    private final AiClientServiceImpl geminiService;
    private final PromptScoreRepository promptScoreRepository;
    private final PromptRepository promptRepository;
    private final PromptVersionRepository promptVersionRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // Scoring weights
    private static final double INSTRUCTION_CLARITY_WEIGHT = 0.15;
    private static final double CONTEXT_COMPLETENESS_WEIGHT = 0.20;
    private static final double OUTPUT_SPECIFICATION_WEIGHT = 0.15;
    private static final double CONSTRAINT_STRENGTH_WEIGHT = 0.15;
    private static final double CURRICULUM_ALIGNMENT_WEIGHT = 0.20;
    private static final double PEDAGOGICAL_QUALITY_WEIGHT = 0.15;

    @Override
    @Transactional
    public PromptScoreResult scorePrompt(String promptText, UUID lessonId) {
        log.info("Starting prompt scoring process");

        // 1. Check Cache
        String cacheKey = "prompt_score:" + hashString(promptText + (lessonId != null ? lessonId : ""));
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("Cache hit for prompt score");
                return objectMapper.readValue(cached, PromptScoreResult.class);
            }
        } catch (Exception e) {
            log.warn("Cache read failed", e);
        }

        // 2. Detect Context (Main Thread)
        CurriculumContext detectedContext = curriculumService.detectContext(promptText);

        UUID finalLessonId = resolveLessonId(promptText, lessonId, detectedContext);

        // 3. Parallel Execution of Scoring Dimensions
        CompletableFuture<DimensionScore> instructionFuture = CompletableFuture
                .supplyAsync(() -> scoreInstructionClarity(promptText));
        CompletableFuture<DimensionScore> contextFuture = CompletableFuture
                .supplyAsync(() -> scoreContextCompleteness(promptText, detectedContext));
        CompletableFuture<DimensionScore> outputFuture = CompletableFuture
                .supplyAsync(() -> scoreOutputSpecification(promptText));
        CompletableFuture<DimensionScore> constraintFuture = CompletableFuture
                .supplyAsync(() -> scoreConstraintStrength(promptText));
        CompletableFuture<DimensionScore> alignmentFuture = CompletableFuture
                .supplyAsync(() -> scoreCurriculumAlignment(promptText, finalLessonId));
        CompletableFuture<DimensionScore> pedagogicalFuture = CompletableFuture
                .supplyAsync(() -> scorePedagogicalQuality(promptText));

        CompletableFuture.allOf(instructionFuture, contextFuture, outputFuture, constraintFuture, alignmentFuture,
                pedagogicalFuture).join();

        DimensionScore instructionClarity = instructionFuture.join();
        DimensionScore contextCompleteness = contextFuture.join();
        DimensionScore outputSpecification = outputFuture.join();
        DimensionScore constraintStrength = constraintFuture.join();
        DimensionScore curriculumAlignment = alignmentFuture.join();
        DimensionScore pedagogicalQuality = pedagogicalFuture.join();

        // 4. Aggregate Results
        double overallScore = calculateOverallScore(
                instructionClarity, contextCompleteness, outputSpecification,
                constraintStrength, curriculumAlignment, pedagogicalQuality);

        Map<String, List<String>> weaknesses = collectWeaknesses(
                instructionClarity, contextCompleteness, outputSpecification,
                constraintStrength, curriculumAlignment, pedagogicalQuality);

        log.info("Scoring completed. Overall score: {}", overallScore);

        PromptScoreResult result = new PromptScoreResult(
                overallScore,
                instructionClarity,
                contextCompleteness,
                outputSpecification,
                constraintStrength,
                curriculumAlignment,
                pedagogicalQuality,
                weaknesses,
                detectedContext);

        // 5. Cache Result
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), Duration.ofHours(24));
        } catch (Exception e) {
            log.warn("Cache write failed", e);
        }

        return result;
    }

    private UUID resolveLessonId(String promptText, UUID lessonId, CurriculumContext detectedContext) {
        if (lessonId == null && detectedContext.getSubjectId() != null && detectedContext.getGradeLevel() != null) {
            LessonSuggestion suggestion = curriculumService.suggestLesson(
                    promptText,
                    detectedContext.getSubjectId(),
                    detectedContext.getGradeLevel());

            if (suggestion != null) {
                log.info("Auto-detected lesson: {}", suggestion.lessonName());
                return suggestion.lessonId();
            }
        }
        return lessonId;
    }

    private DimensionScore scoreInstructionClarity(String promptText) {
        log.debug("Scoring instruction clarity");

        double ruleBasedScore = 0.0;
        List<String> issues = new ArrayList<>();

        Pattern rolePattern = Pattern.compile("(bạn là|you are|act as|role:|vai trò:)", Pattern.CASE_INSENSITIVE);
        if (rolePattern.matcher(promptText).find()) {
            ruleBasedScore += 15;
        } else {
            issues.add("Missing explicit AI role definition");
        }

        Pattern taskPattern = Pattern.compile("(tạo|thiết kế|viết|phát triển|create|design|write|develop|generate)",
                Pattern.CASE_INSENSITIVE);
        if (taskPattern.matcher(promptText).find()) {
            ruleBasedScore += 15;
        } else {
            issues.add("No clear action verb for the task");
        }

        String[] genericPhrases = { "giúp tôi", "help me", "làm cái gì đó", "something", "stuff" };
        boolean isGeneric = Arrays.stream(genericPhrases)
                .anyMatch(phrase -> promptText.toLowerCase().contains(phrase));

        if (!isGeneric && promptText.length() > 50) {
            ruleBasedScore += 15;
        } else {
            issues.add("Task description is too generic or vague");
        }

        String[] ambiguousWords = { "có thể", "maybe", "probably", "something", "stuff", "things" };
        long ambiguousCount = Arrays.stream(ambiguousWords)
                .filter(word -> promptText.toLowerCase().contains(word))
                .count();

        if (ambiguousCount == 0) {
            ruleBasedScore += 15;
        } else {
            issues.add("Contains ambiguous language: " + ambiguousCount + " instances");
        }

        double aiScore = geminiService.scoreInstructionClarity(promptText);
        double totalScore = ruleBasedScore + aiScore;

        return new DimensionScore(
                "Instruction Clarity",
                totalScore,
                100.0,
                ruleBasedScore,
                aiScore,
                issues,
                aiScore < 30 ? List.of("Consider making the instruction more explicit and direct") : List.of());
    }

    private DimensionScore scoreContextCompleteness(String promptText, CurriculumContext context) {
        log.debug("Scoring context completeness");

        double ruleBasedScore = 0.0;
        List<String> issues = new ArrayList<>();

        if (context.getSubject() != null) {
            ruleBasedScore += 10;
        } else {
            issues.add("Subject not specified");
        }

        if (context.getGradeLevel() != null) {
            ruleBasedScore += 10;
        } else {
            issues.add("Grade level not specified");
        }

        Pattern lessonPattern = Pattern.compile("(bài|chương|topic|lesson)", Pattern.CASE_INSENSITIVE);
        if (lessonPattern.matcher(promptText).find()) {
            ruleBasedScore += 10;
        } else {
            issues.add("No specific lesson or topic mentioned");
        }

        Pattern studentPattern = Pattern.compile("(học sinh|students|lớp|class)", Pattern.CASE_INSENSITIVE);
        if (studentPattern.matcher(promptText).find()) {
            ruleBasedScore += 10;
        } else {
            issues.add("No target student group described");
        }

        Pattern durationPattern = Pattern.compile("(\\d+\\s*(phút|tiết|minutes|periods))", Pattern.CASE_INSENSITIVE);
        if (durationPattern.matcher(promptText).find()) {
            ruleBasedScore += 10;
        } else {
            issues.add("Teaching duration not specified");
        }

        Pattern stylePattern = Pattern.compile("(khám phá|thuyết trình|thảo luận|discovery|lecture|discussion)",
                Pattern.CASE_INSENSITIVE);
        if (stylePattern.matcher(promptText).find()) {
            ruleBasedScore += 10;
        }

        Pattern objectivePattern = Pattern.compile("(mục tiêu|objectives|học sinh có thể|students will)",
                Pattern.CASE_INSENSITIVE);
        if (objectivePattern.matcher(promptText).find()) {
            ruleBasedScore += 10;
        }

        double aiScore = geminiService.scoreContextCompleteness(promptText);
        double totalScore = ruleBasedScore + aiScore;

        return new DimensionScore(
                "Context Completeness",
                totalScore,
                100.0,
                ruleBasedScore,
                aiScore,
                issues,
                List.of("Add missing contextual information for better AI output quality"));
    }

    private DimensionScore scoreOutputSpecification(String promptText) {
        log.debug("Scoring output specification");

        double ruleBasedScore = 0.0;
        List<String> issues = new ArrayList<>();

        Pattern formatPattern = Pattern.compile("(format|dạng|định dạng|structure|cấu trúc)", Pattern.CASE_INSENSITIVE);
        if (formatPattern.matcher(promptText).find()) {
            ruleBasedScore += 20;
        } else {
            issues.add("Output format not specified");
        }

        Pattern structurePattern = Pattern.compile("(\\d+\\s*(phần|sections|parts|bước|steps))",
                Pattern.CASE_INSENSITIVE);
        if (structurePattern.matcher(promptText).find()) {
            ruleBasedScore += 15;
        } else {
            issues.add("Output structure not defined");
        }

        Pattern lengthPattern = Pattern.compile("(\\d+\\s*(từ|words|slides|trang|pages)|chi tiết|detailed)",
                Pattern.CASE_INSENSITIVE);
        if (lengthPattern.matcher(promptText).find()) {
            ruleBasedScore += 15;
        } else {
            issues.add("Expected length or detail level not specified");
        }

        double aiScore = geminiService.scoreOutputSpecification(promptText);
        double totalScore = ruleBasedScore + aiScore;

        return new DimensionScore(
                "Output Specification",
                totalScore,
                100.0,
                ruleBasedScore,
                aiScore,
                issues,
                totalScore < 50 ? List.of("Define clear output format and structure expectations") : List.of());
    }

    private DimensionScore scoreConstraintStrength(String promptText) {
        log.debug("Scoring constraint strength");

        double ruleBasedScore = 0.0;
        List<String> issues = new ArrayList<>();

        Pattern curriculumPattern = Pattern.compile("(chương trình|curriculum|SGK|sách giáo khoa|theo|based on)",
                Pattern.CASE_INSENSITIVE);
        if (curriculumPattern.matcher(promptText).find()) {
            ruleBasedScore += 15;
        } else {
            issues.add("No curriculum reference specified");
        }

        Pattern accuracyPattern = Pattern.compile("(chính xác|accurate|đúng|correct|không sai|no errors)",
                Pattern.CASE_INSENSITIVE);
        if (accuracyPattern.matcher(promptText).find()) {
            ruleBasedScore += 10;
        } else {
            issues.add("No accuracy requirements stated");
        }

        Pattern prohibitionPattern = Pattern.compile("(không được|tránh|don't|avoid|không|do not)",
                Pattern.CASE_INSENSITIVE);
        if (prohibitionPattern.matcher(promptText).find()) {
            ruleBasedScore += 15;
        } else {
            issues.add("No constraints or prohibitions defined");
        }

        double aiScore = geminiService.scoreConstraintStrength(promptText);
        double totalScore = ruleBasedScore + aiScore;

        return new DimensionScore(
                "Constraint Strength",
                totalScore,
                100.0,
                ruleBasedScore,
                aiScore,
                issues,
                totalScore < 60 ? List.of("Add constraints to prevent hallucination and off-topic content")
                        : List.of());
    }

    private DimensionScore scoreCurriculumAlignment(String promptText, UUID lessonId) {
        log.debug("Scoring curriculum alignment");

        if (lessonId == null) {
            return new DimensionScore(
                    "Curriculum Alignment",
                    0.0,
                    100.0,
                    0.0,
                    0.0,
                    List.of("No lesson context available for alignment check"),
                    List.of("Specify lesson or provide enough context to detect curriculum alignment"));
        }

        CurriculumContextDetail curriculumContext = curriculumService.getContextDetail(lessonId);
        String contextString = buildCurriculumContextString(curriculumContext);
        double aiScore = geminiService.scoreCurriculumAlignment(promptText, contextString);

        List<String> issues = new ArrayList<>();
        if (aiScore < 50) {
            issues.add("Weak alignment with official curriculum content");
        }

        return new DimensionScore(
                "Curriculum Alignment",
                aiScore,
                100.0,
                0.0,
                aiScore,
                issues,
                aiScore < 70 ? List.of("Align prompt more closely with curriculum learning objectives") : List.of());
    }

    private DimensionScore scorePedagogicalQuality(String promptText) {
        log.debug("Scoring pedagogical quality");

        double ruleBasedScore = 0.0;
        List<String> issues = new ArrayList<>();

        Pattern activityPattern = Pattern.compile("(hoạt động|activity|bài tập|exercise|thực hành|practice)",
                Pattern.CASE_INSENSITIVE);
        if (activityPattern.matcher(promptText).find()) {
            ruleBasedScore += 10;
        } else {
            issues.add("No learning activities mentioned");
        }

        Pattern assessmentPattern = Pattern.compile("(đánh giá|assessment|kiểm tra|quiz|test|câu hỏi|questions)",
                Pattern.CASE_INSENSITIVE);
        if (assessmentPattern.matcher(promptText).find()) {
            ruleBasedScore += 10;
        } else {
            issues.add("No assessment component included");
        }

        double aiScore = geminiService.scorePedagogicalQuality(promptText);
        double totalScore = ruleBasedScore + aiScore;

        return new DimensionScore(
                "Pedagogical Quality",
                totalScore,
                100.0,
                ruleBasedScore,
                aiScore,
                issues,
                totalScore < 60 ? List.of("Enhance with active learning and assessment strategies") : List.of());
    }

    private double calculateOverallScore(DimensionScore... dimensions) {
        return dimensions[0].score() * INSTRUCTION_CLARITY_WEIGHT +
                dimensions[1].score() * CONTEXT_COMPLETENESS_WEIGHT +
                dimensions[2].score() * OUTPUT_SPECIFICATION_WEIGHT +
                dimensions[3].score() * CONSTRAINT_STRENGTH_WEIGHT +
                dimensions[4].score() * CURRICULUM_ALIGNMENT_WEIGHT +
                dimensions[5].score() * PEDAGOGICAL_QUALITY_WEIGHT;
    }

    private Map<String, List<String>> collectWeaknesses(DimensionScore... dimensions) {
        Map<String, List<String>> weaknesses = new HashMap<>();
        for (DimensionScore dimension : dimensions) {
            if (dimension.score() < 70) {
                weaknesses.put(dimension.dimensionName(), dimension.issues());
            }
        }
        return weaknesses;
    }

    private String buildCurriculumContextString(CurriculumContextDetail context) {
        return String.format("""
                Môn học: %s
                Khối: %d
                Học kỳ: %d
                Chương %d: %s
                Bài %d: %s

                Nội dung bài học:
                %s
                """,
                context.subjectName(),
                context.gradeLevel(),
                context.semester(),
                context.chapterNumber(),
                context.chapterName(),
                context.lessonNumber(),
                context.lessonName(),
                context.lessonContent());
    }

    @Override
    public void savePromptScore(UUID promptId, UUID versionId, PromptScoreResult scoreResult) {
        doSavePromptScore(promptId, versionId, scoreResult);
    }

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void savePromptScoreAsync(UUID promptId, UUID versionId, PromptScoreResult scoreResult) {
        try {
            doSavePromptScore(promptId, versionId, scoreResult);
        } catch (Exception e) {
            log.error("Failed to save PromptScore async for prompt: {}", promptId, e);
        }
    }

    private void doSavePromptScore(UUID promptId, UUID versionId, PromptScoreResult scoreResult) {
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("prompt not found with id: " + promptId));

        PromptVersion version = null;
        if (versionId != null) {
            version = promptVersionRepository.findByPromptIdOrderByVersionNumberDesc(promptId).stream()
                    .filter(v -> v.getId().equals(versionId))
                    .findFirst()
                    .orElse(null);
        }

        Map<String, Object> weaknessesMap = new HashMap<>(scoreResult.weaknesses());

        PromptScore score = PromptScore.builder()
                .prompt(prompt)
                .promptId(promptId)
                .version(version)
                .versionId(versionId)
                .overallScore(BigDecimal.valueOf(scoreResult.overallScore()))
                .instructionClarityScore(BigDecimal.valueOf(scoreResult.instructionClarity().score()))
                .contextCompletenessScore(BigDecimal.valueOf(scoreResult.contextCompleteness().score()))
                .outputSpecificationScore(BigDecimal.valueOf(scoreResult.outputSpecification().score()))
                .constraintStrengthScore(BigDecimal.valueOf(scoreResult.constraintStrength().score()))
                .curriculumAlignmentScore(BigDecimal.valueOf(scoreResult.curriculumAlignment().score()))
                .pedagogicalQualityScore(BigDecimal.valueOf(scoreResult.pedagogicalQuality().score()))
                .detectedWeaknesses(weaknessesMap)
                .detectedContext(convertContextToMap(scoreResult.detectedContext()))
                .build();

        promptScoreRepository.save(score);
    }

    private Map<String, Object> convertContextToMap(CurriculumContext context) {
        Map<String, Object> map = new HashMap<>();
        if (context.getSubject() != null)
            map.put("subject", context.getSubject());
        if (context.getGradeLevel() != null)
            map.put("gradeLevel", context.getGradeLevel());
        if (context.getSemester() != null)
            map.put("semester", context.getSemester());
        if (context.getDetectedKeywords() != null)
            map.put("keywords", context.getDetectedKeywords());
        return map;
    }

    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
