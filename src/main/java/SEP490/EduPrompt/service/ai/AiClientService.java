package SEP490.EduPrompt.service.ai;


import SEP490.EduPrompt.model.Prompt;

public interface AiClientService {
    String testPrompt(Prompt prompt, String aiModel, String inputText, Double temperature, Integer maxTokens, Double topP);

    String optimizePrompt(Prompt prompt, String optimizationInput, Double temperature, Integer maxTokens);
}