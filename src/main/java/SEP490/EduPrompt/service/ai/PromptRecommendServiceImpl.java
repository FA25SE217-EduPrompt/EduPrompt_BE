package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.response.prompt.PromptResponse;
import SEP490.EduPrompt.model.Collection;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.repo.PromptRepository;
import SEP490.EduPrompt.repo.TeacherProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.google.common.base.Strings.nullToEmpty;
import static java.util.Optional.ofNullable;

@Service
@Slf4j
@RequiredArgsConstructor
public class PromptRecommendServiceImpl implements PromptRecommendService {
    private final TeacherProfileRepository teacherProfileRepository;
    private final PromptRepository promptRepository;

    private static final Set<String> CORE_SUBJECT_KEYWORDS = Set.of(
            "toán", "toan", "lý", "vật lý", "vat ly", "hóa", "hoa", "hóa học",
            "văn", "ngữ văn", "van", "sinh", "sinh học", "sử", "lịch sử", "su",
            "địa", "địa lý", "dia"
    );

    private static final Set<String> HIGH_SCHOOL_GRADES = Set.of("10", "11", "12");

    @Override
    @Transactional
    public List<PromptResponse> getRecommendedPrompts(UUID userId) {
        boolean isHighSchoolCoreTeacher = isHighSchoolCoreTeacher(userId);

        List<Prompt> candidates;

        if (isHighSchoolCoreTeacher) {
            candidates = promptRepository.findRandomHighSchoolCorePrompts(4);
            log.info("High-school core prompts found: {} (limit was 4)", candidates.size());

            if (candidates.size() < 4) {
                int needed = 4 - candidates.size();
                log.info("Need {} more prompts → querying fallback", needed);

                List<Prompt> fallback = promptRepository.findRandomPublicPrompts(needed);
                log.info("Fallback public prompts found: {}", fallback.size());

                candidates.addAll(fallback);
            } else {
                log.info("Enough high-school prompts, no fallback needed");
            }
        } else {
            candidates = promptRepository.findRandomPublicPrompts(4);
            log.info("Direct public prompts: {}", candidates.size());
        }

        log.info("Final candidates count before mapping: {}", candidates.size());
        return candidates.stream()
                .map(this::toPromptResponse)
                .collect(Collectors.toList()).reversed();
    }

    private boolean isHighSchoolCoreTeacher(UUID userId) {
        return teacherProfileRepository.findByUserId(userId)
                .map(profile -> {
                    String subjects = ofNullable(profile.getSubjectSpecialty()).orElse("").toLowerCase();
                    String grades   = ofNullable(profile.getGradeLevels()).orElse("").toLowerCase();

                    boolean hasCoreSubject = CORE_SUBJECT_KEYWORDS.stream()
                            .anyMatch(subjects::contains);

                    boolean hasHighSchoolGrade = HIGH_SCHOOL_GRADES.stream()
                            .anyMatch(grades::contains);

                    return hasCoreSubject && hasHighSchoolGrade;
                })
                .orElse(false);
    }

    private PromptResponse toPromptResponse(Prompt prompt) {
        User creator = prompt.getUser();
        Collection collection = prompt.getCollection();

        return PromptResponse.builder()
                .id(prompt.getId())
                .title(prompt.getTitle())
                .description(prompt.getDescription())
                .outputFormat(prompt.getOutputFormat())
                .visibility(prompt.getVisibility())
                .fullName(creator != null
                        ? String.join(" ",
                        nullToEmpty(creator.getFirstName()),
                        nullToEmpty(creator.getLastName())).trim()
                        : "Unknown")
                .collectionName(collection != null ? collection.getName() : null)
                .createdAt(prompt.getCreatedAt())
                .updatedAt(prompt.getUpdatedAt())
                .build();
    }
}
