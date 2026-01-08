package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.constant.PromptTemplateConstants;
import SEP490.EduPrompt.dto.request.prompt.GeneratePromptFromFileRequest;
import SEP490.EduPrompt.dto.response.prompt.ClientPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.GeneratePromptFromFileResponse;
import SEP490.EduPrompt.dto.response.prompt.PromptSections;
import SEP490.EduPrompt.exception.client.AiProviderException;
import SEP490.EduPrompt.exception.generic.InvalidFileException;
import SEP490.EduPrompt.util.FileValidationUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PromptGenerationServiceImpl implements PromptGenerationService {

    //    private final QuotaService quotaService;
    private final AiClientService aiClientService;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String UPLOAD_TOPIC = "file:upload";

    @Override
    @Transactional
    public GeneratePromptFromFileResponse generatePromptFromFile(
            UUID userId,
            MultipartFile file,
            GeneratePromptFromFileRequest request) {

        log.info("User {} requesting prompt generation for task type: {}",
                userId, request.promptTask());

        // Validate quota
//        quotaService.validateAndDecrementGenerationQuota(userId);

        FileValidationUtil.validateFile(file);

        java.io.File tempFile = null;
        try {
            tempFile = saveTemporaryFile(file);

            // Upload to Gemini
            File geminiFile = aiClientService.uploadFileToGemini(
                    tempFile,
                    file.getOriginalFilename(),
                    file.getContentType()
            );

            log.info("File uploaded to Gemini successfully for user {}", userId);

            String template = PromptTemplateConstants.getTemplate(request.promptTask());

            ClientPromptResponse aiResponse = aiClientService.generatePromptFromFileContext(
                    geminiFile,
                    template,
                    request.customInstruction(),
                    null // default model : GEMINI_2_5_FLASH
            );

            log.info("AI response received for user {}", userId);

            // Parse the response into 5 sections
            PromptSections sections = parsePromptSections(aiResponse.content());

            queueCloudinaryUpload(userId, tempFile, file);

            GeneratePromptFromFileResponse response = sections.toResponse(
                    aiResponse.model(),
                    aiResponse.promptTokens(),
                    aiResponse.completionTokens(),
                    aiResponse.totalTokens()
            );

            log.info("Prompt generation completed successfully for user {}", userId);
            return response;

        } catch (Exception e) {
            log.error("Error generating prompt from file for user {}: {}",
                    userId, e.getMessage(), e);
            throw new AiProviderException("Failed to generate prompt: " + e.getMessage(), e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                try {
                    Files.deleteIfExists(tempFile.toPath());
                    log.info("Temporary file deleted: {}", tempFile.getName());
                } catch (Exception e) {
                    log.warn("Failed to delete temporary file: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Queue file upload to Cloudinary via Redis Pub/Sub
     */
    private void queueCloudinaryUpload(
            UUID userId,
            java.io.File tempFile,
            MultipartFile originalFile) {

        try {
            // Create upload event
            QueueEventListener.FileUploadEvent event = QueueEventListener.FileUploadEvent.builder()
                    .userId(userId)
                    .tempFilePath(tempFile.getAbsolutePath())
                    .originalFilename(originalFile.getOriginalFilename())
                    .contentType(originalFile.getContentType())
                    .fileSize(originalFile.getSize())
                    .build();

            // Serialize to JSON
            String message = objectMapper.writeValueAsString(event);

            // Publish to Redis
            redisTemplate.convertAndSend(UPLOAD_TOPIC, message);

            log.info("Queued Cloudinary upload for file: {}", originalFile.getOriginalFilename());

        } catch (Exception e) {
            log.error("Failed to queue Cloudinary upload, file will not be saved", e);
            // Not fatal - prompt generation succeeded, only attachment save failed
        }
    }

    /**
     * Save uploaded file to temporary location
     */
    private java.io.File saveTemporaryFile(MultipartFile file) {
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".tmp";

            Path tempFile = Files.createTempFile("prompt-gen-", extension);
            file.transferTo(tempFile.toFile());

            log.info("Temporary file created: {}", tempFile);
            return tempFile.toFile();

        } catch (Exception e) {
            log.error("Failed to save temporary file: {}", e.getMessage(), e);
            throw new InvalidFileException("Failed to process uploaded file: " + e.getMessage());
        }
    }

    /**
     * Parse AI response into structured prompt sections
     */
    private PromptSections parsePromptSections(String aiResponse) {
        try {
            String cleanedResponse = aiResponse
                    .replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();

            PromptSections sections = objectMapper.readValue(cleanedResponse, PromptSections.class);

            // Validate that all required fields are present
            if (sections.instruction() == null || sections.instruction().isBlank()) {
                throw new AiProviderException("AI response missing 'instruction' field");
            }
            if (sections.context() == null || sections.context().isBlank()) {
                throw new AiProviderException("AI response missing 'context' field");
            }

            log.info("Successfully parsed prompt sections from AI response");
            return sections;

        } catch (Exception e) {
            log.error("Failed to parse AI response into prompt sections: {}", e.getMessage());
            log.info("AI response content: {}", aiResponse);
            throw new AiProviderException(
                    "Failed to parse AI response. The AI may have returned invalid format.",
                    e
            );
        }
    }
}