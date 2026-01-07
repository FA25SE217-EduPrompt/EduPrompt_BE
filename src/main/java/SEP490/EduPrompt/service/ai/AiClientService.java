package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.response.prompt.ClientPromptResponse;
import SEP490.EduPrompt.enums.AiModel;
import SEP490.EduPrompt.enums.OptimizationMode;
import SEP490.EduPrompt.model.Prompt;

import java.util.List;
import java.util.Map;

public interface AiClientService {
    ClientPromptResponse testPrompt(Prompt prompt, AiModel aiModel, String inputText, Double temperature,
            Integer maxTokens, Double topP);

    ClientPromptResponse optimizePrompt(Prompt prompt, String optimizationInput, Double temperature, Integer maxTokens);

    double scoreInstructionClarity(String promptText);

    double scoreContextCompleteness(String promptText);

    double scoreOutputSpecification(String promptText);

    double scoreConstraintStrength(String promptText);

    double scoreCurriculumAlignment(String promptText, String curriculumContext);

    double scorePedagogicalQuality(String promptText);

    String optimizePrompt(String promptText, OptimizationMode mode, String curriculumContext,
            Map<String, List<String>> selectedWeaknesses, String customInstruction);
}