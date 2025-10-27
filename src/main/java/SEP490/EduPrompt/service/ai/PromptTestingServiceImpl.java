package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.request.prompt.PromptTestRequest;
import SEP490.EduPrompt.dto.response.prompt.PromptTestResponse;
import SEP490.EduPrompt.enums.AiModel;
import SEP490.EduPrompt.enums.QuotaType;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.model.PromptUsage;
import SEP490.EduPrompt.repo.PromptRepository;
import SEP490.EduPrompt.repo.PromptUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PromptTestingServiceImpl implements PromptTestingService {

    private final QuotaService quotaService;
    private final PromptRepository promptRepository;
    private final PromptUsageRepository usageRepository;
    private final AiClientService aiClientService;

    @Override
    @Transactional
    public PromptTestResponse testPrompt(UUID userId, PromptTestRequest request, String idempotencyKey) {
        log.info("Testing prompt: {} for user: {} with idempotency key: {}",
                request.promptId(), userId, idempotencyKey);

        // Check idempotency - if already processed, return cached result
        //TODO: use redis to cache this key, no need to query this key in db again
        Optional<PromptUsage> existingUsage = usageRepository.findByIdempotencyKey(idempotencyKey);
        if (existingUsage.isPresent()) {
            log.info("Idempotent retry detected for key: {}, returning cached result", idempotencyKey);
            return mapToResponse(existingUsage.get());
        }

        // validate and decrement quota
        //TODO: need to handle token usage
        quotaService.validateAndDecrementQuota(userId, QuotaType.TEST);

        // Fetch prompt
        Prompt prompt = promptRepository.findById(request.promptId())
                .orElseThrow(() -> new ResourceNotFoundException("prompt not found with id: " + request.promptId()));

        // Call AI model
        long startTime = System.currentTimeMillis();
        String output = aiClientService.testPrompt(
                prompt,
                request.aiModel().getName(),
                request.inputText(),
                request.temperature(),
                request.maxTokens(),
                request.topP()
        );
        int executionTime = (int) (System.currentTimeMillis() - startTime);

        // Calculate tokens (approximate - for accurate count, parse OpenAI response)
        int tokensUsed = estimateTokens(output);

        // Save usage with idempotency key
        PromptUsage usage = PromptUsage.builder()
                .promptId(request.promptId())
                .userId(userId)
                .aiModel(request.aiModel().getName())
                .inputText(request.inputText())
                .output(output)
                .tokensUsed(tokensUsed)
                .executionTimeMs(executionTime)
                .idempotencyKey(idempotencyKey)
                .temperature(request.temperature())
                .maxTokens(request.maxTokens())
                .topP(request.topP())
                .createdAt(Instant.now())
                .build();

        PromptUsage savedUsage = usageRepository.save(usage);

        log.info("Prompt test completed. Usage ID: {}, Execution time: {}ms",
                savedUsage.getId(), executionTime);

        return mapToResponse(savedUsage);
    }

    @Override
    @Transactional(readOnly = true)
    public PromptTestResponse getTestResult(UUID usageId) {
        log.info("Fetching test result: {}", usageId);

        PromptUsage usage = usageRepository.findById(usageId)
                .orElseThrow(() -> new RuntimeException("Prompt usage not found: " + usageId));

        return mapToResponse(usage);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PromptTestResponse> getUserTestHistory(UUID userId, Pageable pageable) {
        log.info("Fetching test history for user: {}", userId);

        Page<PromptUsage> usagePage = usageRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return usagePage.map(this::mapToResponse);
    }

    private PromptTestResponse mapToResponse(PromptUsage usage) {
        return new PromptTestResponse(
                usage.getId(),
                usage.getPromptId(),
                AiModel.parseAiModel(usage.getAiModel()),
                usage.getInputText(),
                usage.getOutput(),
                usage.getTokensUsed(),
                usage.getExecutionTimeMs(),
                usage.getTemperature(),
                usage.getMaxTokens(),
                usage.getTopP(),
                usage.getCreatedAt()
        );
    }

    private int estimateTokens(String text) {
        //TODO: might use openai tiktoken to calculate this
        return text != null ? text.length() / 4 : 0;
    }
}