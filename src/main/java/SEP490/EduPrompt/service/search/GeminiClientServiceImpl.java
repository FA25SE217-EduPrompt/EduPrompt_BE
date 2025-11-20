package SEP490.EduPrompt.service.search;

import SEP490.EduPrompt.dto.response.search.FileSearchStoreResponse;
import SEP490.EduPrompt.dto.response.search.FileUploadResponse;
import SEP490.EduPrompt.dto.response.search.GroundingChunk;
import SEP490.EduPrompt.dto.response.search.ImportOperationResponse;
import SEP490.EduPrompt.enums.IndexStatus;
import SEP490.EduPrompt.enums.Visibility;
import SEP490.EduPrompt.exception.client.GeminiApiException;
import SEP490.EduPrompt.exception.generic.InvalidActionException;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.model.PromptTag;
import SEP490.EduPrompt.model.Tag;
import SEP490.EduPrompt.repo.PromptRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiClientServiceImpl implements GeminiClientService {

    private final Client genAiClient;
    private final PromptTagRepository promptTagRepository;
    private final PromptRepository promptRepository;

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
                    .storeId(store.name().orElseThrow())
                    .displayName(store.displayName().orElseThrow())
                    .createdAt(Instant.now())
                    .activeDocumentCount(0L)
                    .build();

            log.info("Created File Search Store: {}", response.storeId());
            return response;

        } catch (ClientException | NoSuchElementException e) {
            log.error("Error creating File Search Store: {}", e.getMessage(), e);
            throw new GeminiApiException("Failed to create File Search Store: " + e.getMessage(), e);
        }
    }

    @Override
    public FileUploadResponse uploadToFileSearchStore(String fileSearchStoreId, Prompt prompt) {
        try {
            if (!Visibility.PUBLIC.name().equalsIgnoreCase(prompt.getVisibility())) {
                throw new InvalidActionException("Cannot upload non-public prompt!");
            }

            log.info("Uploading prompt {} to File Search Store {}", prompt.getId(), fileSearchStoreId);

            String content = buildPromptContent(prompt);
            String displayName = "prompt_" + prompt.getId();

            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            int size = bytes.length;
            InputStream contentStream = new ByteArrayInputStream(bytes);

            UploadToFileSearchStoreOperation operation = genAiClient.fileSearchStores.uploadToFileSearchStore(
                    fileSearchStoreName,
                    contentStream,
                    size,
                    UploadToFileSearchStoreConfig.builder()
                            .displayName(displayName)
                            .customMetadata(buildMetadataSection(prompt))
                            .mimeType("text/plain")
                            .build());

            String operationId = operation.name().orElseThrow();
            // as i observe, i found that operationId and documentId (after operation is done) is similar to each other, just have different prefix
            String documentId = operation.response().orElseThrow()
                    .documentName()
                    .orElse(null) == null
                    ? operationId.replace("upload/operations/", "documents/") : null;

//            String documentId = operationId.replace("upload/operations/", "documents/");
            boolean done = operation.done().orElse(false);

            FileUploadResponse response = FileUploadResponse.builder()
                    .documentId(documentId)
                    .operationId(operationId)
                    .status(done ? IndexStatus.INDEXED.name() : IndexStatus.PENDING.name())
                    .promptId(prompt.getId())
                    .build();

            log.info("Successfully submitted prompt {} for upload. Operation: {}",
                    prompt.getId(), operationId);
            return response;

        } catch (ClientException | NoSuchElementException e) {
            log.error("Error uploading prompt {} to File Search Store: {}",
                    prompt.getId(), e.getMessage(), e);
            throw new GeminiApiException("Failed to upload to File Search Store: " + e.getMessage(), e);
        }
    }

    @Override
    public ImportOperationResponse pollOperation(String operationName) {
        try {
            log.debug("Polling operation: {}", operationName);

            UploadToFileSearchStoreOperation operation = UploadToFileSearchStoreOperation.builder()
                    .name(operationName)
                    .build();

            GetOperationConfig config = GetOperationConfig.builder().build();
            UploadToFileSearchStoreOperation uploadOperation = genAiClient.operations.get(operation, config);

            boolean done = uploadOperation.done().orElse(false);

            String documentId = null;
            String status = IndexStatus.PENDING.name();

            if (done) {
                // Extract document ID when operation completes
                if (uploadOperation.response().isPresent() &&
                        uploadOperation.response().get().documentName().isPresent()) {
                    documentId = uploadOperation.response().get().documentName().get();
                    status = IndexStatus.INDEXED.name();
                    log.info("Operation {} completed with document ID: {}", operationName, documentId);
                } else {
                    status = "failed";
                    log.error("Operation {} completed but no document ID found", operationName);
                }
            }

            return ImportOperationResponse.builder()
                    .operationName(operationName)
                    .done(done)
                    .status(status)
                    .documentId(documentId)
                    .build();

        } catch (ClientException | NoSuchElementException e) {
            log.error("Error polling operation {}: {}", operationName, e.getMessage());

            return ImportOperationResponse.builder()
                    .operationName(operationName)
                    .done(true)
                    .status("failed")
                    .errorMessage(e.getMessage())
                    .build();
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
            // Don't throw - deletion failure shouldn't break flow
        }
    }

    @Override
    public Pager<Document> listDocumentsInStore(String fileSearchStoreId) {
        try {
            log.info("Listing documents in store: {}", fileSearchStoreId);

            ListDocumentsConfig documentsConfig = ListDocumentsConfig.builder()
                    .pageSize(10)
                    .build();
            Pager<Document> documents = genAiClient.fileSearchStores.documents
                    .list(fileSearchStoreId, documentsConfig);

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
                    .storeId(store.name().orElseThrow())
                    .displayName(store.displayName().orElse("Unnamed Store"))
                    .activeDocumentCount(store.activeDocumentsCount().orElse(0L))
                    .build();

        } catch (ClientException | NoSuchElementException e) {
            log.error("Error getting File Search Store {}: {}", fileSearchStoreId, e.getMessage());
            throw new GeminiApiException("Failed to get File Search Store: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFileSearchStore(String fileSearchStoreId, boolean force) {
        try {
            log.info("Deleting File Search Store: {} (force: {})", fileSearchStoreId, force);

            DeleteFileSearchStoreConfig config = DeleteFileSearchStoreConfig.builder()
                    .force(force)
                    .build();
            genAiClient.fileSearchStores.delete(fileSearchStoreId, config);

            log.info("Successfully deleted File Search Store: {}", fileSearchStoreId);

        } catch (ClientException e) {
            log.error("Error deleting File Search Store {}: {}", fileSearchStoreId, e.getMessage());
            throw new GeminiApiException("Failed to delete File Search Store: " + e.getMessage(), e);
        }
    }

    @Override
    public List<GroundingChunk> searchDocuments(String fileSearchStoreId, String query, int maxResults) {
        try {
            log.info("Searching in store {} with query: {}", fileSearchStoreId, query);

            GenerateContentConfig config = GenerateContentConfig.builder()
                    .tools(Collections.singletonList(Tool.builder()
                            .fileSearch(FileSearch.builder()
                                    .fileSearchStoreNames(fileSearchStoreId)
                                    .build())
                            .build()))
                    .build();

            String searchPrompt = String.format(
                    "Find the most relevant teaching prompts for: %s",
                    query);

            GenerateContentResponse response = genAiClient.models.generateContent(
                    "gemini-2.5-flash",
                    searchPrompt,
                    config);

            List<GroundingChunk> chunks = new ArrayList<>();

            if (response.candidates().isPresent() && !response.candidates().get().isEmpty()) {
                Candidate candidate = response.candidates().get().getFirst();

                if (candidate.groundingMetadata().isPresent() &&
                        candidate.groundingMetadata().get().groundingChunks().isPresent()) {

                    int rank = 0; // Track relevance rank

                    for (com.google.genai.types.GroundingChunk chunk : candidate.groundingMetadata().get()
                            .groundingChunks().get()) {

                        if (chunk.retrievedContext().isPresent()) {
                            String documentTitle = chunk.retrievedContext().get().title().orElse(null);
                            String text = chunk.retrievedContext().get().text().orElse(null);

                            if (documentTitle != null && text != null) {
                                //document name or displayName is different from file name or displayName
                                //format of a document name : fileSearchStores/eduprompt-5fuy52dsj4vf/documents/upgs8mhhg64a-p1u11mg22tf8
                                //this documentNameWithTitle is : fileSearchStores/eduprompt-5fuy52dsj4vf/documents/upgs8mhhg64a
                                //upgs8mhhg64a : document title, which is its filename before import to FileSearchStore and become a document
                                //p1u11mg22tf8 : document displayName
                                String documentNameWithTitle = fileSearchStoreId + "/documents/" + documentTitle;

                                var promptOpt = promptRepository.findByGeminiFileIdStartingWith(documentNameWithTitle);
                                if (promptOpt.isPresent()) {
                                    Prompt prompt = promptOpt.get();

                                    // New Strategy: Calculate synthetic confidence score based on Rank.
                                    // The API returns chunks sorted by relevance (index 0 is best).
                                    // 1st result = 0.99, 2nd = 0.98, etc.
                                    Double score = Math.max(0.1, 0.99 - (rank * 0.01));

                                    chunks.add(GroundingChunk.builder()
                                            .documentId(prompt.getGeminiFileId())
                                            .text(text)
                                            .confidenceScore(score)
                                            .build());
                                } else {
                                    log.warn("No prompt found with geminiFileId starting with: {}",
                                            documentNameWithTitle);
                                }
                            } else {
                                log.warn(
                                        "Skipping chunk with missing documentId or text. DocumentId: {}, Text present: {}",
                                        documentTitle, text != null);
                            }
                            rank++;
                        }
                    }
                }
            }

            if (chunks.size() > maxResults) {
                chunks = chunks.subList(0, maxResults);
            }

            log.info("Found {} grounding chunks for query", chunks.size());
            return chunks;

        } catch (ClientException | NoSuchElementException e) {
            log.error("Error searching documents: {}", e.getMessage(), e);
            throw new GeminiApiException("Failed to search documents: " + e.getMessage(), e);
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

        return content.toString().trim();
    }

    private List<CustomMetadata> buildMetadataSection(Prompt prompt) {
        List<CustomMetadata> metadataList = new ArrayList<>();

        List<PromptTag> promptTagList = promptTagRepository.findByPromptId(prompt.getId());
        for (PromptTag promptTag : promptTagList) {
            Tag tag = promptTag.getTag();
            CustomMetadata metadata = CustomMetadata.builder()
                    .key(tag.getType())
                    .stringValue(tag.getValue())
                    .build();
            metadataList.add(metadata);
        }

        // Add visibility metadata
        metadataList.add(CustomMetadata.builder()
                .key("visibility")
                .stringValue(prompt.getVisibility())
                .build());

        return metadataList;
    }
}