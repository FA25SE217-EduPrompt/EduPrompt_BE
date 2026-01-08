package SEP490.EduPrompt.service.prompt;

import SEP490.EduPrompt.dto.request.prompt.CreatePromptVersionRequest;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.model.PromptVersion;
import SEP490.EduPrompt.repo.PromptRepository;
import SEP490.EduPrompt.repo.PromptVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    public PromptVersion createVersion(Prompt prompt, CreatePromptVersionRequest request,
            UUID editorId, UUID lessonId) {
        log.info("Creating version for prompt: {}", prompt.getId());

        // Get next version number
        // Check if we can use count or need max. PromptServiceImpl used max from list.
        // countByPromptId might be faster but if a version was deleted it might reuse
        // numbers?
        // Let's stick to max if possible, but PromptServiceImpl logic was:
        // versions = findByPromptIdOrderByVersionNumberDesc -> first + 1
        List<PromptVersion> versions = versionRepository.findByPromptIdOrderByVersionNumberDesc(prompt.getId());
        int nextVersion = 1;
        if (!versions.isEmpty()) {
            nextVersion = versions.getFirst().getVersionNumber() + 1;
        }

        // Create new version
        PromptVersion version = PromptVersion.builder()
                .prompt(prompt)
                .instruction(request.instruction())
                .context(request.context())
                .inputExample(request.inputExample())
                .outputFormat(request.outputFormat())
                .constraints(request.constraints())
                .editorId(editorId)
                .versionNumber(nextVersion)
                .isAiGenerated(request.isAiGenerated())
                .createdAt(Instant.now())
                .build();

        PromptVersion saved = versionRepository.save(version);

        // Update prompt's current version and content
        prompt.setCurrentVersionId(saved.getId());
        prompt.setCurrentVersion(saved); // Maintain object reference consistency

        prompt.setInstruction(request.instruction());
        prompt.setContext(request.context());
        prompt.setInputExample(request.inputExample());
        prompt.setOutputFormat(request.outputFormat());
        prompt.setConstraints(request.constraints());
        prompt.setUpdatedAt(Instant.now());
        prompt.setUpdatedBy(editorId);

        if (lessonId != null) {
            prompt.setLessonId(lessonId);
        }
        promptRepository.save(prompt);

        log.info("Created version {} for prompt {}", nextVersion, prompt.getId());

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
