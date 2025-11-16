package SEP490.EduPrompt.service.search;

import SEP490.EduPrompt.dto.response.search.FileUploadResponse;
import SEP490.EduPrompt.exception.client.GeminiApiException;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.model.PromptTag;
import SEP490.EduPrompt.repo.PromptTagRepository;
import com.google.genai.Client;
import com.google.genai.types.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiClientServiceImpl implements GeminiClientService {

    private final Client geminiClient;
    private final PromptTagRepository promptTagRepository;

    @Value("${gemini.file.store.name:eduprompt}")
    private String fileStoreName;

    public FileSearchStore createPromptStore(String displayName) {
        try {
            log.info("Attempting to create new FileSearchStore with display name: {}", displayName);
            CreateFileSearchStoreConfig config = CreateFileSearchStoreConfig.builder()
                    .displayName(displayName)
                    .build();
            FileSearchStore store = geminiClient.fileSearchStores.create(config);
            log.info("Successfully created store: {} (Name: {})", store.displayName(), store.name());
            return store;
        } catch (Exception e) {
            log.error("Error creating FileSearchStore: {}", e.getMessage(), e);
            throw new GeminiApiException("Failed to create FileSearchStore: " + e.getMessage(), e);
        }
    }


    @Override
    public FileUploadResponse uploadPromptToFileSearch(Prompt prompt) {
        File tempUploadedFile = null;
        try {
            log.info("Uploading prompt {} to Gemini FileSearchStore: {}", prompt.getId(), fileStoreName);

            String content = buildPromptContent(prompt);
            InputStream contentStream = new ByteArrayInputStream(
                    content.getBytes(StandardCharsets.UTF_8)
            );
            int size = content.getBytes().length;
            String displayName = "prompt_" + prompt.getId().toString();

            // upload using file api
            log.info("Uploading content for prompt {} to general File API", prompt.getId());
            UploadFileConfig uploadConfig = UploadFileConfig.builder()
                    .mimeType("text/plain")
                    .displayName(displayName)
                    .build();

            tempUploadedFile = geminiClient.files.upload(
                    contentStream,
                    size,
                    uploadConfig);
            log.info("Content uploaded. File name: {}", tempUploadedFile.name());


            // then import the uploaded file to fileSearchStore
            log.info("Importing file {} into store {}", tempUploadedFile.name(), fileStoreName);


            //should add metadata to this config
            ImportFileConfig importConfig = ImportFileConfig.builder().build();

            ImportFileOperation operation = geminiClient.fileSearchStores
                    .importFile(fileStoreName, String.valueOf(tempUploadedFile.name()), importConfig);

            log.info("Import operation started. Waiting for completion...");

            ImportFileResponse response = operation.response().orElseThrow(() -> new GeminiApiException("Import operation failed"));
            String fileName = response.documentName().orElseThrow(() -> new GeminiApiException("Import operation failed"));

            log.info("Successfully imported prompt {} with file name: {} ",
                    prompt.getId(), fileName);

            return FileUploadResponse.builder()
                    .fileId(fileName)
                    .status("active")
                    .promptId(prompt.getId())
                    .build();

        } catch (Exception e) {
            log.error("Error uploading prompt {} to Gemini: {}", prompt.getId(), e.getMessage(), e);

            if (tempUploadedFile != null) {
                cleanupTempFile(String.valueOf(tempUploadedFile.name()));
            }
            throw new GeminiApiException("Failed to upload prompt to Gemini: " + e.getMessage(), e);
        }
    }

    private void cleanupTempFile(String fileName) {
        try {
            log.warn("Cleaning up temporary file: {}", fileName);

            DeleteFileConfig config = DeleteFileConfig.builder().build();
            geminiClient.files.delete(fileName, config);

            log.info("Successfully cleaned up temp file {}", fileName);
        } catch (Exception deleteEx) {
            log.error("Failed to clean up temp file {}: {}", fileName, deleteEx.getMessage());
        }
    }

    @Override
    public void deleteFile(String fileName) {
        try {
            log.info("Deleting file {} from Gemini store", fileName);
            DeleteFileSearchStoreConfig config = DeleteFileSearchStoreConfig.builder()
                    .force(true)
                    .build();
            geminiClient.fileSearchStores.delete(fileStoreName, config);
            log.info("Successfully deleted file {}", fileName);
        } catch (Exception e) {
            log.error("Error deleting file {} from Gemini: {}", fileName, e.getMessage(), e);
        }
    }

    @Override
    public FileSearchStore getFile(String fileName) {
        try {
            log.info("Getting file metadata for {} from store", fileName);

            GetFileSearchStoreConfig config = GetFileSearchStoreConfig.builder().build();
            FileSearchStore file = geminiClient.fileSearchStores.get(fileStoreName, config);
            log.info("File: {}", fileName);

            return file;
        } catch (Exception e) {
            log.error("Error getting file metadata for {}: {}", fileName, e.getMessage());
            throw new GeminiApiException("Failed to get file metadata: " + e.getMessage(), e);
        }
    }


    private String buildPromptContent(Prompt prompt) {
        StringBuilder content = new StringBuilder();

        if (prompt.getTitle() != null && !prompt.getTitle().isBlank()) {
            content.append("Title: ").append(prompt.getTitle()).append("\n\n");
        }
        if (prompt.getDescription() != null && !prompt.getDescription().isBlank()) {
            content.append("Description: ").append(prompt.getDescription()).append("\n\n");
        }
        if (prompt.getInstruction() != null && !prompt.getInstruction().isBlank()) {
            content.append("Instruction: ").append(prompt.getInstruction()).append("\n\n");
        }
        if (prompt.getContext() != null && !prompt.getContext().isBlank()) {
            content.append("Context: ").append(prompt.getContext()).append("\n\n");
        }
        if (prompt.getOutputFormat() != null && !prompt.getOutputFormat().isBlank()) {
            content.append("Output Format: ").append(prompt.getOutputFormat()).append("\n\n");
        }
        content.append(buildMetadataSection(prompt));
        return content.toString().trim();
    }

    private String buildMetadataSection(Prompt prompt) {
        StringBuilder metadata = new StringBuilder("Metadata:\n");
        metadata.append("- Visibility: ").append(prompt.getVisibility()).append("\n");
        List<PromptTag> tagList = promptTagRepository.findByPromptId(prompt.getId());
        for (PromptTag tag : tagList) {
            metadata.append("- Tag: ").append(tag.getTag()).append("\n");
        }
        return metadata.toString();
    }
}