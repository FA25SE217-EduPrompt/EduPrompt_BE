package SEP490.EduPrompt.service.ai;


import SEP490.EduPrompt.dto.response.prompt.ClientPromptResponse;
import SEP490.EduPrompt.enums.AiModel;
import SEP490.EduPrompt.model.Prompt;
import com.google.genai.types.File;

public interface AiClientService {
    ClientPromptResponse testPrompt(Prompt prompt, AiModel aiModel, String inputText, Double temperature, Integer maxTokens, Double topP);

    ClientPromptResponse optimizePrompt(Prompt prompt, String optimizationInput, Double temperature, Integer maxTokens);

    /**
     * Generate structured prompt from file context
     *
     * @param file              Gemini file object (already uploaded)
     * @param template          The prompt template with placeholders
     * @param customInstruction Optional custom requirements from teacher
     * @param model             AI model to use
     * @return Response containing generated prompt sections and metadata
     */
    ClientPromptResponse generatePromptFromFileContext(
            File file,
            String template,
            String customInstruction,
            String model
    );

    /**
     * Upload file to Gemini for use as context
     */
    File uploadFileToGemini(java.io.File file, String fileName, String mimeType);
}