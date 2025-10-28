package SEP490.EduPrompt.service.ai;


import SEP490.EduPrompt.dto.response.prompt.ClientPromptResponse;
import SEP490.EduPrompt.model.Prompt;

public interface AiClientService {
    ClientPromptResponse testPrompt(Prompt prompt, String aiModel, String inputText, Double temperature, Integer maxTokens, Double topP);

    ClientPromptResponse optimizePrompt(Prompt prompt, String optimizationInput, Double temperature, Integer maxTokens);
}