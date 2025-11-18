package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.request.prompt.PromptTestRequest;
import SEP490.EduPrompt.dto.response.prompt.ClientPromptResponse;
import SEP490.EduPrompt.enums.QueueStatus;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.model.PromptUsage;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.repo.PromptUsageRepository;
import SEP490.EduPrompt.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromptUsageServiceImpl implements PromptUsageService{
    private final PromptUsageRepository promptUsageRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PromptUsage saveUsage(
            UUID userId,
            Prompt prompt,
            PromptTestRequest request,
            ClientPromptResponse aiResponse,
            int tokensUsed,
            long executionTime,
            String idempotencyKey) {

        log.debug("Saving prompt usage for user {} with idempotency key {}",
                userId, idempotencyKey);

        User user = userRepository.getReferenceById(userId);
        PromptUsage usage = PromptUsage.builder()
                .prompt(prompt)
                .user(user)
                .userId(userId)
                .promptId(prompt.getId())
                .aiModel(request.aiModel().getName())
                .inputText(request.inputText())
                .output(aiResponse.content())
                .tokensUsed(tokensUsed)
                .executionTimeMs((int) executionTime)
                .idempotencyKey(idempotencyKey)
                .temperature(request.temperature())
                .maxTokens(request.maxTokens())
                .topP(request.topP())
                .status(QueueStatus.COMPLETED.name())
                .createdAt(Instant.now())
                .build();

        return promptUsageRepository.save(usage);
    }
}
