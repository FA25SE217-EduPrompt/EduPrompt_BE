package SEP490.EduPrompt.service.search;

import SEP490.EduPrompt.dto.response.search.*;
import SEP490.EduPrompt.model.Prompt;
import com.google.genai.Pager;
import com.google.genai.types.Document;

import java.util.List;

/**
 * Service for interacting with Gemini File Search API
 */
public interface GeminiClientService {

    /**
     * Create a new File Search Store (container for documents/prompts)
     *
     * @param displayName Human-readable name for the store
     * @return FileSearchStoreResponse with store ID
     */
    FileSearchStoreResponse createFileSearchStore(String displayName);

    /**
     * Upload file directly to File Search Store
     * This combines upload + chunking + indexing in one operation
     *
     * @param fileSearchStoreId The store ID (format: fileSearchStores/xxx)
     * @param prompt            The prompt to upload
     * @return FileUploadResponse with document ID
     */
    FileUploadResponse uploadToFileSearchStore(String fileSearchStoreId, Prompt prompt);

    /**
     * Poll operation status (for import operations)
     *
     * @param operationName The operation name returned from import
     * @return Operation status (done: true/false)
     */
    ImportOperationResponse pollOperation(String operationName);

    /**
     * Get file/document metadata from File Search Store
     *
     * @param documentName The document name (format: fileSearchStores/xxx/documents/yyy)
     * @return DocumentResponse metadata
     */
    DocumentResponse getDocument(String documentName);

    /**
     * Delete document from File Search Store
     *
     * @param documentName The document name
     */
    void deleteDocument(String documentName);

    /**
     * List all documents in a File Search Store
     *
     * @param fileSearchStoreId The store ID
     * @return List of document names
     */
    List<DocumentResponse> listDocumentsInStore(String fileSearchStoreId);

    /**
     * Get File Search Store details
     *
     * @param fileSearchStoreId The store ID
     * @return Store metadata including document count
     */
    FileSearchStoreResponse getFileSearchStore(String fileSearchStoreId);

    /**
     * Delete entire File Search Store (and all documents)
     *
     * @param fileSearchStoreId The store ID
     * @param force             If true, delete even if store has documents
     */
    void deleteFileSearchStore(String fileSearchStoreId, boolean force);

    /**
     * Perform semantic search using Gemini's generateContent with grounding
     * @param fileSearchStoreId The store to search in
     * @param query User's search query
     * @param maxResults Maximum number of results to return
     * @return List of grounding chunks with matched content
     */
    List<GroundingChunk> searchDocuments(String fileSearchStoreId, String query, int maxResults);
}