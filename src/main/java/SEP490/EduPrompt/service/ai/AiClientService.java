package SEP490.EduPrompt.service.ai;


import SEP490.EduPrompt.dto.response.prompt.ClientPromptResponse;
import SEP490.EduPrompt.enums.AiModel;
import SEP490.EduPrompt.model.Prompt;

import java.io.File;

public interface AiClientService {
    ClientPromptResponse testPrompt(Prompt prompt, AiModel aiModel, String inputText, Double temperature, Integer maxTokens, Double topP);

    ClientPromptResponse optimizePrompt(Prompt prompt, String optimizationInput, Double temperature, Integer maxTokens);

    ClientPromptResponse generatePromptWithContext(File file, String fileName, String mineType);
}