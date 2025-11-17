package SEP490.EduPrompt.service.search;

import SEP490.EduPrompt.dto.response.search.FileSearchStoreResponse;
import SEP490.EduPrompt.dto.response.search.FileUploadResponse;
import SEP490.EduPrompt.dto.response.search.ImportOperationResponse;
import SEP490.EduPrompt.exception.client.GeminiApiException;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.model.PromptTag;
import SEP490.EduPrompt.model.Tag;
import SEP490.EduPrompt.repo.PromptTagRepository;
import com.google.genai.Client;
import com.google.genai.Pager;
import com.google.genai.errors.ClientException;
import com.google.genai.types.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiClientServiceImpl implements GeminiClientService {

    private final Client genAiClient;
    private final PromptTagRepository promptTagRepository;

    @Value("${gemini.file-search-store}")
    private String fileSearchStoreName;

    @Override
    public FileSearchStoreResponse createFileSearchStore(String displayName) {
        try {
            log.info("Creating File Search Store: {}", displayName);

            CreateFileSearchStoreConfig request = CreateFileSearchStoreConfig.builder()
                    .displayName(displayName)
                    .build();
            FileSearchStore store = genAiClient.fileSearchStores.create(request);

            FileSearchStoreResponse response = FileSearchStoreResponse.builder()
                    .storeId(String.valueOf(store.name()))
                    .displayName(String.valueOf(store.displayName()))
                    .createdAt(Instant.now())
                    .activeDocumentCount(0L)
                    .build();

            log.info("Created File Search Store: {}", response.storeId());
            return response;

        } catch (ClientException e) {
            log.error("Error creating File Search Store: {}", e.getMessage(), e);
            throw new GeminiApiException("Failed to create File Search Store: " + e.getMessage(), e);
        }
    }

    @Override
    public FileUploadResponse uploadToFileSearchStore(String fileSearchStoreId, Prompt prompt) {
        try {
            log.info("Uploading prompt {} to File Search Store {}", prompt.getId(), fileSearchStoreId);

            String content = buildPromptContent(prompt);
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            int size = bytes.length;
            InputStream contentStream = new ByteArrayInputStream(bytes);


            String displayName = "prompt_" + prompt.getId();

            UploadFileConfig uploadFileConfig = UploadFileConfig.builder()
                    .displayName(displayName)
                    .mimeType("text/plain")
                    .build();

            File newFile = genAiClient.files.upload(contentStream, size, uploadFileConfig);

            log.info("Uploaded new file : {}", newFile);

            ImportFileConfig importFileConfig = ImportFileConfig.builder()
//                    .customMetadata(buildMetadataSection(prompt))
                    .build();
            ImportFileOperation operation = genAiClient.fileSearchStores.importFile(
                    fileSearchStoreName,
                    String.valueOf(newFile.name()),
                    importFileConfig
            );

            //it will return a operation object with status done=false, just get its name, i will poll it later
            String operationId = String.valueOf(operation.name());
            boolean done = operation.done().isPresent() ? operation.done().get() : false;
            FileUploadResponse response = FileUploadResponse.builder()
                    .documentId(operationId) //just a placeholder
                    .operationId(operationId)
                    .status(done ? "active" : "processing")
                    .promptId(prompt.getId())
                    .build();

            log.info("Successfully uploaded prompt {} with operation {}", prompt.getId(), operationId);
            return response;

        } catch (ClientException e) {
            log.error("Error uploading prompt {} to File Search Store: {}", prompt.getId(), e.getMessage(), e);
            throw new GeminiApiException("Failed to upload to File Search Store: " + e.getMessage(), e);
        }
    }

    @Override
    public ImportOperationResponse importFileToStore(String fileSearchStoreId, String fileId, String displayName) {
        try {
            log.info("Importing file {} into File Search Store {}", fileId, fileSearchStoreId);

            ImportFileOperation operation = genAiClient.fileSearchStores.importFile(
                    fileSearchStoreName,
                    String.valueOf(fileId),
                    ImportFileConfig.builder().build()
            );

            //it will return a operation object with status done=false, just get its name, i will poll it later
            String operationName = String.valueOf(operation.name());
            ImportOperationResponse response = ImportOperationResponse.builder()
                    .documentId(operationName)
                    .operationName(operationName)
                    .done(false)
                    .status("processing")
                    .build();

            log.info("Started import operation: {}", response.operationName());
            return response;

        } catch (ClientException e) {
            log.error("Error importing file {} to store: {}", fileId, e.getMessage(), e);
            throw new GeminiApiException("Failed to import file: " + e.getMessage(), e);
        }
    }

    @Override
    public ImportOperationResponse pollOperation(String operationName) {
        try {
            log.debug("Polling operation: {}", operationName);

            ImportFileOperation operation = ImportFileOperation.builder()
                    .name(operationName)
                    .build();

            GetOperationConfig config = GetOperationConfig.builder().build();
            ImportFileOperation importFileOperation = genAiClient.operations.get(operation, config);

            boolean done = importFileOperation.done().isPresent() ? importFileOperation.done().get() : false;

            ImportOperationResponse response = ImportOperationResponse.builder()
                    .operationName(operationName)
                    .done(done)
                    .status("completed")
                    .documentId(String.valueOf(importFileOperation.name()))
                    .build();

            if (response.done()) {
                log.info("Operation {} completed", operationName);
            }

            return response;

        } catch (ClientException e) {
            log.error("Error polling operation {}: {}", operationName, e.getMessage());
            throw new GeminiApiException("Failed to poll operation: " + e.getMessage(), e);
        }
    }

    @Override
    public Document getDocument(String documentName) {
        try {
            log.info("Getting document: {}", documentName);

            GetDocumentConfig documentsConfig = GetDocumentConfig.builder().build();

            return genAiClient.fileSearchStores.documents.get(documentName, documentsConfig);

        } catch (ClientException e) {
            log.error("Error getting document {}: {}", documentName, e.getMessage());
            throw new GeminiApiException("Failed to get document: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteDocument(String documentName) {
        try {
            log.info("Deleting document: {}", documentName);

            DeleteDocumentConfig deleteDocumentConfig = DeleteDocumentConfig.builder().build();
            genAiClient.fileSearchStores.documents.delete(documentName, deleteDocumentConfig);

            log.info("Successfully deleted document: {}", documentName);

        } catch (ClientException e) {
            log.error("Error deleting document {}: {}", documentName, e.getMessage());
            // don't throw - deletion failure shouldn't break flow
        }
    }

    @Override
    public Pager<Document> listDocumentsInStore(String fileSearchStoreId) {
        try {
            log.info("Listing documents in store: {}", fileSearchStoreId);

            ListDocumentsConfig documentsConfig = ListDocumentsConfig.builder()
                    .pageSize(10).build();
            Pager<Document> documents = genAiClient.fileSearchStores.documents.list(fileSearchStoreId, documentsConfig);

            log.info("Found {} documents in store {}", documents.size(), fileSearchStoreId);
            return documents;

        } catch (ClientException e) {
            log.error("Error listing documents in store {}: {}", fileSearchStoreId, e.getMessage());
            throw new GeminiApiException("Failed to list documents: " + e.getMessage(), e);
        }
    }

    @Override
    public FileSearchStoreResponse getFileSearchStore(String fileSearchStoreId) {
        try {
            log.info("Getting File Search Store: {}", fileSearchStoreId);

            GetFileSearchStoreConfig config = GetFileSearchStoreConfig.builder().build();
            FileSearchStore store = genAiClient.fileSearchStores.get(fileSearchStoreId, config);


            return FileSearchStoreResponse.builder()
                    .storeId(String.valueOf(store.name()))
                    .displayName(String.valueOf(store.displayName()))
                    .activeDocumentCount(store.activeDocumentsCount().isPresent() ? store.activeDocumentsCount().get() : 0L)
                    .build();

        } catch (ClientException e) {
            log.error("Error getting File Search Store {}: {}", fileSearchStoreId, e.getMessage());
            throw new GeminiApiException("Failed to get File Search Store: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFileSearchStore(String fileSearchStoreId, boolean force) {
        try {
            log.info("Deleting File Search Store: {} (force: {})", fileSearchStoreId, force);

            DeleteFileSearchStoreConfig config = DeleteFileSearchStoreConfig.builder()
                    .force(force) // this is dangerous
                    .build();
            genAiClient.fileSearchStores.delete(fileSearchStoreId, config);
            log.info("Successfully deleted File Search Store: {}", fileSearchStoreId);

        } catch (ClientException e) {
            log.error("Error deleting File Search Store {}: {}", fileSearchStoreId, e.getMessage());
            throw new GeminiApiException("Failed to delete File Search Store: " + e.getMessage(), e);
        }
    }

    /**
     * Build searchable content from prompt
     */
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

        return content.toString().trim();
    }

//    /**
//     * Build custom metadata from prompt
//     */
//    private List<CustomMetadata> buildMetadataSection(Prompt prompt) {
//        List<CustomMetadata> metadataList = new ArrayList<>();
//
//        List<PromptTag> promptTagList = promptTagRepository.findByPromptId(prompt.getId());
//        for (PromptTag promptTag : promptTagList) {
//            Tag tag = promptTag.getTag();
//            CustomMetadata metadata = CustomMetadata.builder()
//                    .key(tag.getType())
//                    .stringValue(tag.getValue())
//                    .build();
//            metadataList.add(metadata);
//        }
//        return metadataList;
//    }
}