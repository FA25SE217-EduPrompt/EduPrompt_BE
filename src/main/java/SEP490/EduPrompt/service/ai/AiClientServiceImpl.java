package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.constant.PromptTemplateConstants;
import SEP490.EduPrompt.dto.response.prompt.ClientPromptResponse;
import SEP490.EduPrompt.enums.AiModel;
import SEP490.EduPrompt.exception.client.AiProviderException;
import SEP490.EduPrompt.model.Prompt;
import com.google.common.collect.ImmutableList;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Service
@Slf4j
@RequiredArgsConstructor
public class AiClientServiceImpl implements AiClientService {

    private final Client geminiClient;
    private final OpenAIClient openAiClient;

    @Value("${ai.timeout.read:30}")
    private int readTimeoutSeconds;

    private static final Integer DEFAULT_MAX_TOKEN = 8192;
    private static final Float DEFAULT_TEMPERATURE = 0.3f;
    private static final Float DEFAULT_TOP_P = 0.7f;

    @Override
    public ClientPromptResponse testPrompt(
            Prompt prompt,
            AiModel aiModel,
            String inputText,
            Double temperature,
            Integer maxTokens,
            Double topP) {

        log.info("Testing prompt {} with model: {}", prompt.getId(), aiModel.getName());

        String fullPrompt = buildFullPrompt(prompt, inputText);

        try {
            return switch (aiModel) {
                case GEMINI_2_5_FLASH -> callGeminiApi(fullPrompt, aiModel.getName(), temperature, maxTokens, topP);
                case GPT_4O_MINI -> callOpenAiApi(fullPrompt, aiModel.getName(), temperature, maxTokens, topP);
                case CLAUDE_3_5_SONNET -> callAnthropicApi(fullPrompt, aiModel.getName(), temperature, maxTokens, topP);
                default -> throw new AiProviderException("Unsupported AI model: " + aiModel.getName());
            };
        } catch (AiProviderException e) {
            // Already wrapped, re-throw
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error calling AI model {}", aiModel.getName(), e);
            throw new AiProviderException("AI call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public ClientPromptResponse optimizePrompt(
            Prompt prompt,
            String optimizationInput,
            Double temperature,
            Integer maxTokens) {

        log.info("Optimizing prompt {}", prompt.getId());

        String optimizationPrompt = buildOptimizationPrompt(prompt, optimizationInput);

        // Use GPT-4O-MINI for optimization , might change this later
        return callOpenAiApi(
                optimizationPrompt,
                AiModel.GPT_4O_MINI.getName(),
                temperature,
                maxTokens,
                1.0
        );
    }

    @Override
    public File uploadFileToGemini(java.io.File file, String fileName, String mimeType) {
        log.info("Uploading file {} to Gemini", fileName);

        try {
            UploadFileConfig config = UploadFileConfig.builder()
                    .displayName(fileName)
                    .mimeType(mimeType)
                    .build();

            File geminiFile = geminiClient.files.upload(file, config);

            log.info("File uploaded successfully: {}", geminiFile.uri().orElse("unknown"));
            return geminiFile;

        } catch (Exception e) {
            log.error("Failed to upload file to Gemini: {}", e.getMessage(), e);
            throw new AiProviderException("Failed to upload file to Gemini: " + e.getMessage(), e);
        }
    }

    @Override
    public ClientPromptResponse generatePromptFromFileContext(
            File file,
            String template,
            String customInstruction,
            String model) {

        String effectiveModel = (model != null && !model.isBlank())
                ? model
                : AiModel.GEMINI_2_5_FLASH.getName();

        long startTime = System.currentTimeMillis();

        log.debug("Generating prompt from file with model: {}", effectiveModel);

        try {
            // Build the user prompt
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("Based on the uploaded document, generate a structured prompt following this template:\n\n");
            promptBuilder.append(template);

            if (customInstruction != null && !customInstruction.isBlank()) {
                promptBuilder.append("\n\n### Additional Requirements from Teacher:\n");
                promptBuilder.append(customInstruction);
            }

            promptBuilder.append("\n\nIMPORTANT: Return your response as a valid JSON object with these exact fields: ");
            promptBuilder.append("instruction, context, input_example, output_format, constraints");

            String userPrompt = promptBuilder.toString();

            // Build generation config
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .temperature(DEFAULT_TEMPERATURE)
                    .maxOutputTokens(DEFAULT_MAX_TOKEN)
                    .topP(DEFAULT_TOP_P)
                    .build();

            // Build content with file and prompt
            Content content = Content.builder()
                    .role("user")
                    .parts(
                            Part.builder().text(userPrompt).build(),
                            Part.builder()
                                    .fileData(FileData.builder()
                                            .fileUri(file.uri().get())
                                            .mimeType(file.mimeType().get())
                                            .build())
                                    .build()
                    )
                    .build();

            // System instruction
            Content systemInstruction = Content.fromParts(
                    Part.fromText(PromptTemplateConstants.buildSystemInstruction())
            );

            // Safety settings
            ImmutableList<SafetySetting> safetySettings = ImmutableList.of(
                    SafetySetting.builder()
                            .category(HarmCategory.Known.HARM_CATEGORY_HATE_SPEECH)
                            .threshold(HarmBlockThreshold.Known.BLOCK_ONLY_HIGH)
                            .build(),
                    SafetySetting.builder()
                            .category(HarmCategory.Known.HARM_CATEGORY_DANGEROUS_CONTENT)
                            .threshold(HarmBlockThreshold.Known.BLOCK_LOW_AND_ABOVE)
                            .build()
            );

            // Generate content
            GenerateContentResponse response = geminiClient.models.generateContent(
                    effectiveModel,
                    content,
                    config.toBuilder()
                            .safetySettings(safetySettings)
                            .systemInstruction(systemInstruction)
                            .build()
            );

            long duration = System.currentTimeMillis() - startTime;
            log.info("Prompt generation completed in {}ms", duration);

            // Extract response
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

            return ClientPromptResponse.builder()
                    .content(responseContent)
                    .prompt(userPrompt)
                    .model(effectiveModel)
                    .temperature(Double.valueOf(DEFAULT_TEMPERATURE))
                    .maxTokens(DEFAULT_MAX_TOKEN)
                    .topP(Double.valueOf(DEFAULT_TOP_P))
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .finishReason(finishReason)
                    .id(null)
                    .createdAt(Instant.now())
                    .build();

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Error generating prompt after {}ms: {}", duration, e.getMessage(), e);

            if (e.getCause() instanceof java.net.SocketTimeoutException) {
                throw new AiProviderException("Prompt generation timed out", e);
            }
            throw new AiProviderException("Failed to generate prompt: " + e.getMessage(), e);
        }
    }

    /**
     * Call OpenAI API with timeout handling
     */
    protected ClientPromptResponse callOpenAiApi(
            String prompt,
            String model,
            Double temperature,
            Integer maxTokens,
            Double topP) {

        long startTime = System.currentTimeMillis();

        try {
            log.debug("Calling OpenAI API with model: {}", model);

            // Build the chat completion request
            ChatCompletionCreateParams.Builder requestBuilder = ChatCompletionCreateParams.builder()
                    .model(ChatModel.GPT_4O)
                    .addMessage(ChatCompletionUserMessageParam.builder()
                            .content(ChatCompletionUserMessageParam.Content.ofText(prompt))
                            .build()
                    );

            // Add optional parameters
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

            // Call OpenAI API with configured timeouts
            ChatCompletion completion = openAiClient.chat().completions().create(request);

            long duration = System.currentTimeMillis() - startTime;
            log.info("OpenAI API call completed in {}ms", duration);

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
            long duration = System.currentTimeMillis() - startTime;
            log.error("OpenAI service error after {}ms: {} - {}", duration, e.statusCode(), e.getMessage());

            // more specific error messages
            String errorMsg = switch (e.statusCode()) {
                case 429 -> "Rate limit exceeded. Please try again later.";
                case 500, 502, 503 -> "OpenAI service temporarily unavailable. Please retry.";
                case 401 -> "Invalid API key configuration.";
                default -> "OpenAI API error: " + e.getMessage();
            };

            throw new AiProviderException(errorMsg, e);

        } catch (OpenAIException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("OpenAI SDK error after {}ms: {}", duration, e.getMessage());
            throw new AiProviderException("Failed to call OpenAI API: " + e.getMessage(), e);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Unexpected error calling OpenAI API after {}ms", duration, e);

            // Check if it's a timeout
            if (e.getCause() instanceof java.net.SocketTimeoutException) {
                throw new AiProviderException(
                        String.format("OpenAI request timed out after %d seconds", readTimeoutSeconds),
                        e
                );
            }

            throw new AiProviderException("Failed to call OpenAI API: " + e.getMessage(), e);
        }
    }

    /**
     * Call Gemini API with timeout handling
     */
    protected ClientPromptResponse callGeminiApi(
            String prompt,
            String model,
            Double temperature,
            Integer maxTokens,
            Double topP) {

        String effectiveModel = (model != null && !model.isBlank())
                ? model
                : AiModel.GEMINI_2_5_FLASH.getName();

        long startTime = System.currentTimeMillis();

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

            // Generate content
            GenerateContentResponse response = geminiClient.models.generateContent(
                    effectiveModel,
                    List.of(content),
                    configBuilder.build()
            );

            long duration = System.currentTimeMillis() - startTime;
            log.info("Gemini API call completed in {}ms", duration);

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
            long duration = System.currentTimeMillis() - startTime;
            log.error("Error calling Gemini API after {}ms with model {}: {}",
                    duration, effectiveModel, e.getMessage(), e);

            // Check for timeout
            if (e.getCause() instanceof java.net.SocketTimeoutException) {
                throw new AiProviderException(
                        String.format("Gemini request timed out after %d seconds", readTimeoutSeconds),
                        e
                );
            }

            throw new AiProviderException("Failed to call Gemini API: " + e.getMessage(), e);
        }
    }

    /**
     * Upload file as context for gemini api request
     */
    protected File uploadFile(java.io.File file, String fileName, String mineType) {
        UploadFileConfig config = UploadFileConfig.builder()
                .displayName(fileName)
                .mimeType(mineType)
                .build();
        return geminiClient.files.upload(file, config);
    }

    /**
     * Call Gemini API with context from file uploaded through gemini file api
     */
    protected ClientPromptResponse callGeminiApiWithContext(
            File file,
            String prompt,
            String model,
            Double temperature,
            Integer maxTokens,
            Double topP) {

        String effectiveModel = (model != null && !model.isBlank())
                ? model
                : AiModel.GEMINI_2_5_FLASH.getName();

        long startTime = System.currentTimeMillis();

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
                    .parts(Part.builder()
                                    .text(prompt)
                                    .build(),
                            Part.builder()
                                    .fileData(FileData.builder()
                                            .fileUri(file.uri().get())
                                            .mimeType(file.mimeType().get())
                                            .build())
                                    .build())
                    .build();

            ImmutableList<SafetySetting> safetySettings =
                    ImmutableList.of(
                            SafetySetting.builder()
                                    .category(HarmCategory.Known.HARM_CATEGORY_HATE_SPEECH)
                                    .threshold(HarmBlockThreshold.Known.BLOCK_ONLY_HIGH)
                                    .build(),
                            SafetySetting.builder()
                                    .category(HarmCategory.Known.HARM_CATEGORY_DANGEROUS_CONTENT)
                                    .threshold(HarmBlockThreshold.Known.BLOCK_LOW_AND_ABOVE)
                                    .build());

            Content systemInstruction = Content.fromParts(Part.fromText("You are an experienced high-school teacher assistant and curriculum designer," +
                    " familiar with secondary education standards and pedagogical best practices."));

            // Generate content
            GenerateContentResponse response = geminiClient.models.generateContent(
                    effectiveModel,
                    content,
                    configBuilder
                            .safetySettings(safetySettings)
                            .systemInstruction(systemInstruction)
                            .thinkingConfig(ThinkingConfig.builder().thinkingBudget(4096))
                            .build()
            );

            long duration = System.currentTimeMillis() - startTime;
            log.info("Gemini API call completed in {}ms", duration);

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
            long duration = System.currentTimeMillis() - startTime;
            log.error("Error calling Gemini API after {}ms with model {}: {}",
                    duration, effectiveModel, e.getMessage(), e);

            // Check for timeout
            if (e.getCause() instanceof java.net.SocketTimeoutException) {
                throw new AiProviderException(
                        String.format("Gemini request timed out after %d seconds", readTimeoutSeconds),
                        e
                );
            }
            throw new AiProviderException("Failed to call Gemini API: " + e.getMessage(), e);
        }
    }

    protected ClientPromptResponse callAnthropicApi(
            String prompt,
            String model,
            Double temperature,
            Integer maxTokens,
            Double topP) {

        //TODO: Implement when i get the $5 credit, im broke (T-T)
        log.warn("Anthropic API not yet implemented");
        throw new AiProviderException("Anthropic API not yet implemented");
    }

    /**
     * this helper method onl for gemini response
     */
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

    /**
     * this helper method onl for gemini response
     */
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

        int minChars = Math.max(800, currentPrompt.length());
        int minWords = Math.max(120, currentPrompt.split("\\s+").length);

        return String.format(
                """
                        System: You are an expert prompt engineer. Strict rules:
                         - Preserve all intent and requirements from the Current Prompt.
                         - Expand and improve the prompt for clarity, pedagogy, and concrete instructions for a high school teacher audience.
                         - Do NOT shorten or remove details. The optimized prompt MUST be AT LEAST %d characters and AT LEAST %d words.
                         - Keep or expand examples, constraints, and formatting. Add step-by-step instructions, examples, and explicit output format if missing.
                         - Follow prompt-engineering best practices: explicit goal, role, audience, constraints, examples, desired output shape.
                         - Return ONLY the optimized prompt text (no commentary, no meta, no labels). If you produce anything else, the response will be discarded.
                                
                        === Current Prompt ===
                        %s
                                
                        === Optimization Request ===
                        %s
                                
                        Output requirements (IMPORTANT):
                        1) Return a single block of plain text (the new prompt).
                        2) The prompt must be ready-to-use — teacher can paste it into an LLM as-is.
                        3) The prompt must be at least %d characters and at least %d words.
                        4) Do not include any preamble like "Here is the optimized prompt:".
                        """,
                minChars, minWords,
                currentPrompt,
                optimizationInput,
                minChars, minWords
        );
    }

}