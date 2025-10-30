package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.response.prompt.ClientPromptResponse;
import SEP490.EduPrompt.enums.AiModel;
import SEP490.EduPrompt.exception.client.AiProviderException;
import SEP490.EduPrompt.model.Prompt;
import com.google.genai.Client;
import com.google.genai.types.*;
import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import com.openai.models.completions.CompletionUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiClientServiceImpl implements AiClientService {

    private static final int TIMEOUT_SECONDS = 30;
    //gemini client
    private final Client client;
    //openai client
    private final OpenAIClient openAiClient;

    @Override
    public ClientPromptResponse testPrompt(Prompt prompt, AiModel aiModel, String inputText,
                                           Double temperature, Integer maxTokens, Double topP) {
        log.info("Testing prompt {} with model: {}", prompt.getId(), aiModel.getName());

        String fullPrompt = buildFullPrompt(prompt, inputText);

        switch (aiModel) {
            case GEMINI_2_5_FLASH -> {
                return callGeminiApi(fullPrompt, aiModel.getName(), temperature, maxTokens, topP);
            }

            case GPT_4O_MINI -> {
                return callOpenAiApi(fullPrompt, aiModel.getName(), temperature, maxTokens, topP);
            }

            case CLAUDE_3_5_SONNET -> {
                return callAnthropicApi(fullPrompt, aiModel.getName(), temperature, maxTokens, topP);
            }

            default -> throw new AiProviderException("Unsupported ai model: " + aiModel.getName());
        }


    }

    @Override
    public ClientPromptResponse optimizePrompt(Prompt prompt, String optimizationInput,
                                               Double temperature, Integer maxTokens) {
        log.info("Optimizing prompt {}", prompt.getId());

        String optimizationPrompt = buildOptimizationPrompt(prompt, optimizationInput);
        //just call gemini api for now, might plan to allow to choose model when optimize
        return callGeminiApi(optimizationPrompt, AiModel.GPT_4O_MINI.getName(), temperature, maxTokens, 1.0);
    }

    public ClientPromptResponse callOpenAiApi(
            String prompt,
            String model,
            Double temperature,
            Integer maxTokens,
            Double topP
    ) {
        try {
            log.debug("Calling OpenAI API with model: {}", model);

            // Build the chat completion request
            ChatCompletionCreateParams.Builder requestBuilder = ChatCompletionCreateParams.builder()
                    .model(ChatModel.GPT_4O_MINI)
                    .addMessage(ChatCompletionUserMessageParam.builder()
                            .content(ChatCompletionUserMessageParam.Content.ofText(prompt))
                            .build()
                    );

            // Add optional parameters only if provided
            if (temperature != null) {
                requestBuilder.temperature(temperature);
            }
            if (maxTokens != null) {
                requestBuilder.maxCompletionTokens(maxTokens.longValue());
            }
            if (topP != null) {
                requestBuilder.topP(topP);
            }

            ChatCompletionCreateParams request = requestBuilder.build();

            // Call OpenAI API using the SDK
            ChatCompletion completion = openAiClient.chat().completions().create(request);

            // Extract response data
            String content = null;
            String finishReason = null;

            if (!completion.choices().isEmpty()) {
                ChatCompletion.Choice choice = completion.choices().getFirst();
                content = choice.message().content().orElse(null);
                finishReason = choice.finishReason().toString();
            }

            CompletionUsage usage = completion.usage().orElse(null);

            return ClientPromptResponse.builder()
                    .content(content)
                    .prompt(prompt)
                    .model(model)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .topP(topP)
                    .promptTokens(usage != null ? Math.toIntExact(usage.promptTokens()) : null)
                    .completionTokens(usage != null ? Math.toIntExact(usage.completionTokens()) : null)
                    .totalTokens(usage != null ? Math.toIntExact(usage.totalTokens()) : null)
                    .finishReason(finishReason)
                    .id(completion.id())
                    .createdAt(Instant.ofEpochSecond(completion.created()))
                    .build();

        } catch (OpenAIServiceException e) {
            log.error("OpenAI service error: {} - {}", e.statusCode(), e.getMessage());
            throw new AiProviderException("OpenAI API error: " + e.getMessage());
        } catch (OpenAIException e) {
            log.error("OpenAI SDK error: {}", e.getMessage());
            throw new AiProviderException("Failed to call OpenAI API: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error calling OpenAI API", e);
            throw new AiProviderException("Failed to call OpenAI API: " + e.getMessage());
        }
    }

    public ClientPromptResponse callGeminiApi(
            String prompt,
            String model,
            Double temperature,
            Integer maxTokens,
            Double topP
    ) {
        String effectiveModel = (model != null && !model.isBlank()) ? model : AiModel.GEMINI_2_5_FLASH.getName();
        log.debug("Calling Gemini API with model: {}", effectiveModel);

        try {
            // Build generation config
            GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder();
            if (temperature != null) {
                configBuilder.temperature(temperature.floatValue());
            }
            if (maxTokens != null) {
                configBuilder.maxOutputTokens(maxTokens);
            }
            if (topP != null) {
                configBuilder.topP(topP.floatValue());
            }

            // Build content with user role and text part
            Content content = Content.builder()
                    .role("user")
                    .parts(List.of(Part.builder().text(prompt).build()))
                    .build();

            // Generate content using the client
            GenerateContentResponse response = client.models.generateContent(
                    effectiveModel,
                    List.of(content),
                    configBuilder.build()
            );
            log.info("Gemini API response: {}", response);

            // Extract response content safely
            String responseContent = extractResponseContent(response);
            String finishReason = extractFinishReason(response);

            // Extract usage metadata
            Integer promptTokens = null;
            Integer completionTokens = null;
            Integer totalTokens = null;

            if (response.usageMetadata().isPresent()) {
                var usageMetadata = response.usageMetadata().get();
                promptTokens = usageMetadata.promptTokenCount().orElse(null);
                completionTokens = usageMetadata.candidatesTokenCount().orElse(null);
                totalTokens = usageMetadata.totalTokenCount().orElse(null);
            }

            // Generate a unique ID
            String responseId = UUID.randomUUID().toString();

            return ClientPromptResponse.builder()
                    .content(responseContent)
                    .prompt(prompt)
                    .model(effectiveModel)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .topP(topP)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .finishReason(finishReason)
                    .id(responseId)
                    .createdAt(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("Error calling Gemini API with model {}: {}", effectiveModel, e.getMessage(), e);
            throw new AiProviderException("Failed to call Gemini API: " + e.getMessage());
        }
    }

    public ClientPromptResponse callAnthropicApi(
            String prompt,
            String model,
            Double temperature,
            Integer maxTokens,
            Double topP
    ){
        //TODO: need another 5$ of starting credit to use
        return null;
    }

    private String extractResponseContent(GenerateContentResponse response) {
        return response.candidates()
                .flatMap(candidates -> candidates.isEmpty() ?
                        java.util.Optional.empty() :
                        java.util.Optional.of(candidates.getFirst()))
                .flatMap(Candidate::content)
                .flatMap(Content::parts)
                .flatMap(parts -> parts.isEmpty() ?
                        java.util.Optional.empty() :
                        java.util.Optional.of(parts.getFirst()))
                .flatMap(Part::text)
                .orElse("");
    }

    private String extractFinishReason(GenerateContentResponse response) {
        return response.candidates()
                .filter(candidates -> !candidates.isEmpty())
                .map(List::getFirst)
                .flatMap(Candidate::finishReason)
                .map(FinishReason::toString)
                .orElse("UNKNOWN");
    }


    private String buildFullPrompt(Prompt prompt, String inputText) {
        StringBuilder fullPrompt = new StringBuilder();

        if (prompt.getInstruction() != null && !prompt.getInstruction().isBlank()) {
            fullPrompt.append("Instruction: ").append(prompt.getInstruction()).append("\n\n");
        }

        if (prompt.getContext() != null && !prompt.getContext().isBlank()) {
            fullPrompt.append("Context: ").append(prompt.getContext()).append("\n\n");
        }

        if (inputText != null && !inputText.isBlank()) {
            fullPrompt.append("Input: ").append(inputText).append("\n\n");
        } else if (prompt.getInputExample() != null && !prompt.getInputExample().isBlank()) {
            fullPrompt.append("Input Example: ").append(prompt.getInputExample()).append("\n\n");
        }

        if (prompt.getOutputFormat() != null && !prompt.getOutputFormat().isBlank()) {
            fullPrompt.append("Output Format: ").append(prompt.getOutputFormat()).append("\n\n");
        }

        if (prompt.getConstraints() != null && !prompt.getConstraints().isBlank()) {
            fullPrompt.append("Constraints: ").append(prompt.getConstraints()).append("\n\n");
        }

        return fullPrompt.toString().trim();
    }

    private String buildOptimizationPrompt(Prompt prompt, String optimizationInput) {
        String currentPrompt = buildFullPrompt(prompt, null);

        return String.format(
                """
                        You are an expert prompt engineer. Your task is to optimize the following prompt for high school teacher with better clarity, effectiveness, and results.
                                        
                        Current Prompt:
                        %s
                                        
                        Optimization Request:
                        %s
                                        
                        Please provide an improved version of the prompt that:
                        1. Maintains the original intent and requirements
                        2. Improves clarity and structure
                        3. Follows best practices for prompt engineering
                        4. Addresses the specific optimization request
                                        
                        Return only the optimized prompt without explanations.
                        """,
                currentPrompt,
                optimizationInput
        );
    }
}

