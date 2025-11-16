package SEP490.EduPrompt.service.search;

import SEP490.EduPrompt.dto.response.search.FileUploadResponse;
import SEP490.EduPrompt.model.Prompt;
import com.google.genai.types.FileSearchStore;

public interface GeminiClientService {
    /**
     * Upload prompt content to Gemini File API for vector search
     *
     * @param prompt The prompt to index
     * @return FileUploadResponse containing file ID and status
     */
    FileUploadResponse uploadPromptToFileSearch(Prompt prompt);


    /**
     * Delete file from Gemini File API
     *
     * @param fileName The Gemini file name to delete (format: files/xxx)
     */
    void deleteFile(String fileName);

    /**
     * Get file metadata from Gemini
     *
     * @param fileName The Gemini file name
     * @return File metadata
     */
    FileSearchStore getFile(String fileName);
}
