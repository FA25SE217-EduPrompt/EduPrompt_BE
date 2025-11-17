package SEP490.EduPrompt.service.search;

import SEP490.EduPrompt.dto.response.search.FileSearchStoreResponse;
import SEP490.EduPrompt.dto.response.search.FileUploadResponse;
import SEP490.EduPrompt.dto.response.search.ImportOperationResponse;
import SEP490.EduPrompt.model.Prompt;
import com.google.genai.Pager;
import com.google.genai.types.Document;

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
     * Import an already-uploaded file into File Search Store
     * Use this if you've already uploaded via Files API
     *
     * @param fileSearchStoreId The store ID
     * @param fileId            The file ID from Files API (format: files/xxx)
     * @param displayName       Display name for the document
     * @return ImportOperationResponse with operation name for polling
     */
    ImportOperationResponse importFileToStore(String fileSearchStoreId, String fileId, String displayName);

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
     * @return Document metadata
     */
    Document getDocument(String documentName);

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
    Pager<Document> listDocumentsInStore(String fileSearchStoreId);

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
}