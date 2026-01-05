package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.request.prompt.GeneratePromptFromFileRequest;
import SEP490.EduPrompt.dto.response.prompt.GeneratePromptFromFileResponse;
import SEP490.EduPrompt.exception.client.AiProviderException;
import SEP490.EduPrompt.exception.client.QuotaExceededException;
import SEP490.EduPrompt.exception.generic.InvalidFileException;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface PromptGenerationService {

    /**
     * Generate structured prompt from uploaded file using predefined template
     *
     * @param userId  The user requesting generation
     * @param file    The uploaded reference file (curriculum, textbook, etc.)
     * @param request Contains prompt task type and optional custom instruction
     * @return Generated prompt with 5 structured sections
     * @throws QuotaExceededException if user has no generation quota remaining
     * @throws InvalidFileException   if file is invalid or empty
     * @throws AiProviderException    if AI API call fails
     */
    GeneratePromptFromFileResponse generatePromptFromFile(
            UUID userId,
            MultipartFile file,
            GeneratePromptFromFileRequest request
    );
}
