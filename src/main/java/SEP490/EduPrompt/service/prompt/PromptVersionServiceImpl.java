package SEP490.EduPrompt.service.prompt;

import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.model.PromptVersion;
import SEP490.EduPrompt.repo.PromptRepository;
import SEP490.EduPrompt.repo.PromptVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class PromptVersionServiceImpl implements PromptVersionService {

    private final PromptVersionRepository versionRepository;
    private final PromptRepository promptRepository;

    @Override
    public PromptVersion createOptimizedVersion(UUID promptId, String optimizedContent,
                                                UUID editorId, UUID lessonId) {
        log.info("Creating optimized version for prompt: {}", promptId);

        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found with id: " + promptId));

        // Get next version number
        int nextVersion = versionRepository.countByPromptId(promptId) + 1;

        // Create new version
        PromptVersion version = PromptVersion.builder()
                .prompt(prompt)
                .instruction(optimizedContent)
                .editorId(editorId)
                .versionNumber(nextVersion)
                .isAiGenerated(true)
                .build();

        PromptVersion saved = versionRepository.save(version);

        // Update prompt's current version
        prompt.setCurrentVersionId(saved.getId());
        if (lessonId != null) {
            prompt.setLessonId(lessonId);
        }
        promptRepository.save(prompt);

        log.info("Created version {} for prompt {}", nextVersion, promptId);

        return saved;
    }

    @Override
    public Optional<PromptVersion> findById(UUID versionId) {
        return versionRepository.findById(versionId);
    }

    @Override
    public List<PromptVersion> getVersionHistory(UUID promptId) {
        return versionRepository.findByPromptIdOrderByVersionNumberDesc(promptId);
    }
}