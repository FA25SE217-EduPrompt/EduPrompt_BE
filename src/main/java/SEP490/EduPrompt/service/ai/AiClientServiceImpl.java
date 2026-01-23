package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.constant.PromptTemplateConstants;
import SEP490.EduPrompt.dto.response.prompt.ClientPromptResponse;
import SEP490.EduPrompt.enums.AiModel;
import SEP490.EduPrompt.enums.OptimizationMode;
import SEP490.EduPrompt.exception.client.AiProviderException;
import SEP490.EduPrompt.model.Prompt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiClientServiceImpl implements AiClientService {

    // DEFAULT_MAX_TOKEN is now in AiClientService interface
    private static final Float DEFAULT_TEMPERATURE = 0.3f;
    private static final Float DEFAULT_TOP_P = 0.7f;
    private static final String DEFAULT_MODEL = AiModel.GEMINI_3_FLASH_PREVIEW.getName();
    private static final ImmutableList<SafetySetting> DEFAULT_SAFETY_SETTINGS = ImmutableList.of(
            SafetySetting.builder()
                    .category(HarmCategory.Known.HARM_CATEGORY_HATE_SPEECH)
                    .threshold(HarmBlockThreshold.Known.BLOCK_ONLY_HIGH)
                    .build(),
            SafetySetting.builder()
                    .category(HarmCategory.Known.HARM_CATEGORY_DANGEROUS_CONTENT)
                    .threshold(HarmBlockThreshold.Known.BLOCK_LOW_AND_ABOVE)
                    .build());
    private final Client geminiClient;
    private final OpenAIClient openAiClient;
    private final ObjectMapper objectMapper;
    @Value("${ai.timeout.read:30}")
    private int readTimeoutSeconds;

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
                case GEMINI_2_5_FLASH, GEMINI_3_FLASH_PREVIEW ->
                        callGeminiApi(fullPrompt, aiModel.getName(), temperature, maxTokens, topP);
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
                1.0);
    }

    @Override
    public double scoreInstructionClarity(String promptText) {
        log.debug("Calling Gemini for instruction clarity scoring");

        String systemPrompt = """
                You are an expert in evaluating prompt quality for educational purposes.
                Score the instruction clarity of the given prompt on a scale of 0-40.
                """;

        String userPrompt = String.format("""
                Evaluate the clarity of this instruction (0-40 points):

                %s

                Consider:
                - Is the AI's role crystal clear?
                - Is the task unambiguous?
                - Would different AI models interpret this similarly?

                Return ONLY a JSON object:
                {"score": <0-40>, "reason": "<brief explanation>"}
                """, promptText);

        return callGeminiForScore(systemPrompt, userPrompt);
    }

    @Override
    public double scoreContextCompleteness(String promptText) {
        log.debug("Calling Gemini for context completeness scoring");

        String systemPrompt = """
                You are an expert in evaluating educational prompt context richness.
                Score how complete the contextual information is on a scale of 0-30.
                """;

        String userPrompt = String.format("""
                Rate the contextual richness (0-30 points):

                %s

                Does it provide enough background for generating high-quality educational content?
                Consider: subject details, student characteristics, teaching environment.

                Return ONLY a JSON object:
                {"score": <0-30>, "reason": "<brief explanation>"}
                """, promptText);

        return callGeminiForScore(systemPrompt, userPrompt);
    }

    @Override
    public double scoreOutputSpecification(String promptText) {
        log.debug("Calling Gemini for output specification scoring");

        String systemPrompt = """
                You are an expert in evaluating prompt output specifications.
                Score how well-defined the output requirements are on a scale of 0-50.
                """;

        String userPrompt = String.format("""
                Score output specification clarity (0-50 points):

                %s

                Would a teacher know exactly what format/structure to expect from the AI output?

                Return ONLY a JSON object:
                {"score": <0-50>, "reason": "<brief explanation>"}
                """, promptText);

        return callGeminiForScore(systemPrompt, userPrompt);
    }

    @Override
    public double scoreConstraintStrength(String promptText) {
        log.debug("Calling Gemini for constraint strength scoring");

        String systemPrompt = """
                You are an expert in evaluating prompt constraints and guardrails.
                Score the strength of constraints on a scale of 0-60.
                """;

        String userPrompt = String.format("""
                Evaluate constraint strength (0-60 points):

                %s

                Are there clear guardrails to prevent hallucination, off-topic content, or inaccurate information?

                Return ONLY a JSON object:
                {"score": <0-60>, "reason": "<brief explanation>"}
                """, promptText);

        return callGeminiForScore(systemPrompt, userPrompt);
    }

    @Override
    public double scoreCurriculumAlignment(String promptText, String curriculumContext) {
        log.info("Calling Gemini for curriculum alignment scoring");

        String systemPrompt = """
                You are an expert in Vietnamese high school curriculum evaluation.
                Score how well a prompt aligns with official curriculum content on a scale of 0-100.
                """;

        String userPrompt = String.format(
                """
                        Score curriculum alignment (0-100 points):

                        TEACHER'S PROMPT:
                        %s

                        OFFICIAL CURRICULUM CONTEXT:
                        %s

                        Searching for related lesson content in Vietnamese high school curriculum for better curriculum context if you found it missing.
                        Scoring criteria:
                        1. Does the prompt reference the correct lesson/chapter? (30 points)
                        2. Does it match the learning objectives? (30 points)
                        3. Is the scope appropriate for this grade level? (20 points)
                        4. Are terminology and concepts accurate per curriculum? (20 points)

                        Return ONLY a JSON object:
                        {"score": <0-100>, "issues": ["issue1", "issue2"], "suggestions": ["suggestion1"]}
                        """,
                promptText, curriculumContext);

        try {
            String response = callGeminiApi(systemPrompt, userPrompt, false);
            String jsonString = extractOptimizedPrompt(response);
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            if (jsonNode.has("score")) {
                return jsonNode.get("score").asDouble();
            }
            return 0.0;
        } catch (Exception e) {
            log.error("Error parsing curriculum alignment score", e);
            return 0.0;
        }
    }

    @Override
    public double scorePedagogicalQuality(String promptText) {
        log.info("Calling Gemini for pedagogical quality scoring");

        String systemPrompt = """
                You are an expert in pedagogy and teaching methodologies.
                Score the pedagogical quality of a prompt on a scale of 0-80.
                """;

        String userPrompt = String.format("""
                Evaluate pedagogical quality (0-80 points):

                %s

                Consider:
                - Active learning vs passive learning approach
                - Differentiation for different student levels
                - Assessment integration
                - Alignment with modern teaching methodologies
                - Bloom's taxonomy level

                Return ONLY a JSON object:
                {"score": <0-80>, "reason": "<brief explanation>"}
                """, promptText);

        return callGeminiForScore(systemPrompt, userPrompt);
    }

    @Override
    public String optimizePrompt(String promptText, OptimizationMode mode, String curriculumContext,
                                 Map<String, List<String>> selectedWeaknesses, String customInstruction) {
        log.info("Optimizing prompt with mode: {}", mode);

        String systemPrompt = """
                You are an expert prompt engineer specializing in educational content creation.
                Your task is to optimize teacher prompts while preserving their original intent.
                """;

        String userPrompt = mode == OptimizationMode.SAFE
                ? buildSafeOptimizationPrompt(promptText, curriculumContext, selectedWeaknesses, customInstruction)
                : buildPedagogicalOptimizationPrompt(promptText, curriculumContext, selectedWeaknesses,
                customInstruction);

        try {
            String response = callGeminiApi(systemPrompt, userPrompt, true);
            return extractOptimizedPrompt(response);
        } catch (Exception e) {
            log.error("Error optimizing prompt", e);
            throw new RuntimeException("Failed to optimize prompt", e);
        }
    }

    private String buildSafeOptimizationPrompt(String promptText, String curriculumContext,
                                               Map<String, List<String>> weaknesses, String customInstruction) {
        String weaknessesString = formatWeaknesses(weaknesses);

        return String.format(
                """
                        Optimize this teacher's prompt using SAFE mode:

                        ORIGINAL PROMPT:
                        %s

                        CURRICULUM CONTEXT:
                        %s

                        DETECTED WEAKNESSES:
                        %s

                        USER INSTRUCTIONS:
                        %s

                        OPTIMIZATION RULES (SAFE MODE):
                        1. Preserve teacher's intent 100%% - do not change the core request
                                2. Preserve ALL existing sections (Instruction, Context, Input Example, Output Format and Constraint) - DO NOT DELETE ANY TEXT - ONLY ADD IF TEXT IS TOO SHORT TO FORM A PROPER PROMPT
                                3. Only add missing structural elements identified in weaknesses
                                4. Add output format specification if missing
                                5. Add curriculum reference from the context provided
                                6. Keep the same language and tone
                                7. Make medium additions
                                8. Do not add activities or change teaching approach

                        Return ONLY the optimized prompt text, nothing else.
                        """,
                promptText,
                curriculumContext,
                weaknessesString,
                customInstruction != null ? customInstruction : "None");
    }

    private String buildPedagogicalOptimizationPrompt(String promptText, String curriculumContext,
                                                      Map<String, List<String>> weaknesses, String customInstruction) {
        String weaknessesString = formatWeaknesses(weaknesses);

        return String.format("""
                        Optimize this teacher's prompt using PEDAGOGICAL ENHANCEMENT mode:

                        ORIGINAL PROMPT:
                        %s

                        CURRICULUM CONTEXT:
                        %s

                        DETECTED WEAKNESSES:
                        %s

                        USER INSTRUCTIONS:
                        %s

                        OPTIMIZATION RULES (PEDAGOGICAL MODE):
                        1. Preserve teacher's core intent
                        2. Fix all structural issues from weaknesses
                        3. Add 2-3 active learning activities aligned with the lesson content
                        4. Include differentiation strategies for mixed-ability students
                        5. Add formative assessment component
                        6. Align explicitly with learning objectives from curriculum
                        7. Target appropriate Bloom's taxonomy level (Analysis/Application for high school)
                        8. Maintain Vietnamese educational context and terminology

                        Format the optimized prompt clearly with 5 sections as original prompt (Instruction, Context, Input Example, Output Format, Constraint).
                        Mark new additions with [ADDED] (can add multiple additions for each section if able) at the start of new sections.

                        Return ONLY the optimized prompt text.
                        """,
                promptText,
                curriculumContext,
                weaknessesString,
                customInstruction != null ? customInstruction : "None");
    }

    private String formatWeaknesses(Map<String, List<String>> weaknesses) {
        if (weaknesses == null || weaknesses.isEmpty()) {
            return "None";
        }
        StringBuilder sb = new StringBuilder();
        weaknesses.forEach((category, issues) -> {
            sb.append("- ").append(category).append(":\n");
            issues.forEach(issue -> sb.append("  * ").append(issue).append("\n"));
        });
        return sb.toString();
    }

    private double callGeminiForScore(String systemPrompt, String userPrompt) {
        try {
            String response = callGeminiApi(systemPrompt, userPrompt, false);
            // Clean markdown formatting if present
            String jsonString = extractOptimizedPrompt(response);
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            if (jsonNode.has("score")) {
                return jsonNode.get("score").asDouble();
            }
            return 0.0;
        } catch (Exception e) {
            log.error("Error calling Gemini API for scoring", e);
            return 0.0;
        }
    }

    private String callGeminiApi(String systemPrompt, String userPrompt, Boolean enableGoogleSearch) {
        Content systemInstruction = Content.builder()
                .parts(Part.builder()
                        .text(systemPrompt)
                        .build())
                .build();

        Content prompt = Content.builder()
                .role("user")
                .parts(Part.builder()
                        .text(userPrompt)
                        .build())
                .build();
        Tool.Builder toolBuilder = Tool.builder();
        if (enableGoogleSearch) {
            toolBuilder.googleSearch(GoogleSearch.builder()
                    .build());
        }

        GenerateContentConfig config = GenerateContentConfig.builder()
                .temperature(DEFAULT_TEMPERATURE)
                .maxOutputTokens(DEFAULT_MAX_TOKEN)
                .topP(DEFAULT_TOP_P)
                .safetySettings(DEFAULT_SAFETY_SETTINGS)
                .systemInstruction(systemInstruction)
                .tools(toolBuilder)
                .build();

        try {
            GenerateContentResponse response = geminiClient.models.generateContent(
                    AiModel.GEMINI_3_FLASH_PREVIEW.getName(),
                    prompt,
                    config);

            return extractResponseContent(response);

        } catch (Exception e) {
            log.error("Error calling Gemini API", e);
            throw new RuntimeException("Gemini API call failed", e);
        }
    }

    private String extractOptimizedPrompt(String response) {
        // Remove markdown code blocks (json, text, etc)
        String cleaned = response.trim()
                .replaceAll("^```\\w*\\s*", "")
                .replaceAll("```\\s*$", "")
                .trim();

        // Remove wrapping quotes if present
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }

        return cleaned;
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
                : AiModel.GEMINI_3_FLASH_PREVIEW.getName();

        long startTime = System.currentTimeMillis();

        log.debug("Generating prompt from file with model: {}", effectiveModel);

        try {
            // Build the user prompt
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append(
                    "Based on the uploaded document, generate a structured prompt following this template:\n\n");
            promptBuilder.append(template);

            if (customInstruction != null && !customInstruction.isBlank()) {
                promptBuilder.append("\n\n### Additional Requirements from Teacher:\n");
                promptBuilder.append(customInstruction);
            }

            promptBuilder
                    .append("\n\nIMPORTANT: Return your response as a valid JSON object with these exact fields: ");
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
                                    .build())
                    .build();

            // System instruction
            Content systemInstruction = Content.fromParts(
                    Part.fromText(PromptTemplateConstants.buildSystemInstruction()));

            // Safety settings
            ImmutableList<SafetySetting> safetySettings = ImmutableList.of(
                    SafetySetting.builder()
                            .category(HarmCategory.Known.HARM_CATEGORY_HATE_SPEECH)
                            .threshold(HarmBlockThreshold.Known.BLOCK_ONLY_HIGH)
                            .build(),
                    SafetySetting.builder()
                            .category(HarmCategory.Known.HARM_CATEGORY_DANGEROUS_CONTENT)
                            .threshold(HarmBlockThreshold.Known.BLOCK_LOW_AND_ABOVE)
                            .build());

            // Generate content
            GenerateContentResponse response = geminiClient.models.generateContent(
                    effectiveModel,
                    content,
                    config.toBuilder()
                            .safetySettings(safetySettings)
                            .systemInstruction(systemInstruction)
                            .tools(Tool.builder()
                                    .googleSearch(GoogleSearch.builder().build())
                                    .build())
                            .build());

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
                            .build());

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
                        e);
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
                : DEFAULT_MODEL;

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
                    configBuilder.build());

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
                        e);
            }

            throw new AiProviderException("Failed to call Gemini API: " + e.getMessage(), e);
        }
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
                : AiModel.GEMINI_3_FLASH_PREVIEW.getName();

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

            ImmutableList<SafetySetting> safetySettings = ImmutableList.of(
                    SafetySetting.builder()
                            .category(HarmCategory.Known.HARM_CATEGORY_HATE_SPEECH)
                            .threshold(HarmBlockThreshold.Known.BLOCK_ONLY_HIGH)
                            .build(),
                    SafetySetting.builder()
                            .category(HarmCategory.Known.HARM_CATEGORY_DANGEROUS_CONTENT)
                            .threshold(HarmBlockThreshold.Known.BLOCK_LOW_AND_ABOVE)
                            .build());

            Content systemInstruction = Content.fromParts(
                    Part.fromText("You are an experienced high-school teacher assistant and curriculum designer," +
                            " familiar with secondary education standards and pedagogical best practices."));

            // Generate content
            GenerateContentResponse response = geminiClient.models.generateContent(
                    effectiveModel,
                    content,
                    configBuilder
                            .safetySettings(safetySettings)
                            .systemInstruction(systemInstruction)
                            .thinkingConfig(ThinkingConfig.builder().thinkingBudget(4096))
                            .build());

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
                        e);
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

        // TODO: Implement when i get the $5 credit, im broke (T-T)
        log.warn("Anthropic API not yet implemented");
        throw new AiProviderException("Anthropic API not yet implemented");
    }

    /**
     * this helper method onl for gemini response
     */
    private String extractResponseContent(GenerateContentResponse response) {
        return response.candidates()
                .flatMap(candidates -> candidates.isEmpty() ? java.util.Optional.empty()
                        : java.util.Optional.of(candidates.getFirst()))
                .flatMap(Candidate::content)
                .flatMap(Content::parts)
                .flatMap(
                        parts -> parts.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(parts.getFirst()))
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
                minChars, minWords);
    }

}