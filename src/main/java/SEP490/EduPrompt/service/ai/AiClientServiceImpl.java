package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.response.prompt.ClientPromptResponse;
import SEP490.EduPrompt.enums.AiModel;
import SEP490.EduPrompt.exception.client.AiProviderException;
import SEP490.EduPrompt.model.Prompt;
import com.google.genai.Client;
import com.google.genai.types.*;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AiClientServiceImpl implements AiClientService {

    private static final int TIMEOUT_SECONDS = 30;
    private final WebClient webClient;

    private final Client client;

    @Value("${openai.api-key}")
    private String openaiApiKey;
    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String openaiApiUrl;

    public AiClientServiceImpl(WebClient.Builder webClientBuilder, Client client) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, TIMEOUT_SECONDS * 1000)
                .responseTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(TIMEOUT_SECONDS, TimeUnit.SECONDS)));

        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.client = client;
    }

    @Override
    public ClientPromptResponse testPrompt(Prompt prompt, String aiModel, String inputText,
                                           Double temperature, Integer maxTokens, Double topP) {
        log.info("Testing prompt {} with model: {}", prompt.getId(), aiModel);

        String fullPrompt = buildFullPrompt(prompt, inputText);
        //just call openai api for now
        return callGeminiApi(fullPrompt, aiModel, temperature, maxTokens, topP);
    }

    @Override
    public ClientPromptResponse optimizePrompt(Prompt prompt, String optimizationInput,
                                               Double temperature, Integer maxTokens) {
        log.info("Optimizing prompt {}", prompt.getId());

        String optimizationPrompt = buildOptimizationPrompt(prompt, optimizationInput);
        //just call openai api for now
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

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(
                    Map.of("role", "user", "content", prompt)
            ));

            if (temperature != null) requestBody.put("temperature", temperature);
            if (maxTokens != null) requestBody.put("max_tokens", maxTokens);
            if (topP != null) requestBody.put("top_p", topP);

            Map<String, Object> responseBody = webClient.post()
                    .uri(openaiApiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + openaiApiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("OpenAI API client error: {}", errorBody);
                                        return Mono.error(new AiProviderException(
                                                "OpenAI API client error: " + clientResponse.statusCode()));
                                    }))
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("OpenAI API server error: {}", errorBody);
                                        return Mono.error(new AiProviderException(
                                                "OpenAI API server error: " + clientResponse.statusCode()));
                                    }))
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .block();

            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            Map<String, Object> usage = (Map<String, Object>) responseBody.get("usage");
            String id = (String) responseBody.get("id");
            Long createdSeconds = responseBody.get("created") instanceof Number
                    ? ((Number) responseBody.get("created")).longValue()
                    : null;

            String content = null, finishReason = null;
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                content = (String) message.get("content");
                finishReason = (String) choices.get(0).get("finish_reason");
            }

            return ClientPromptResponse.builder()
                    .content(content)
                    .prompt(prompt)
                    .model(model)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .topP(topP)
                    .promptTokens(usage != null ? (Integer) usage.get("prompt_tokens") : null)
                    .completionTokens(usage != null ? (Integer) usage.get("completion_tokens") : null)
                    .totalTokens(usage != null ? (Integer) usage.get("total_tokens") : null)
                    .finishReason(finishReason)
                    .id(id)
                    .createdAt(createdSeconds != null ? Instant.ofEpochSecond(createdSeconds) : Instant.now())
                    .build();

        } catch (WebClientRequestException e) {
            log.error("Request error calling OpenAI API: {}", e.getMessage());
            throw new AiProviderException("Failed to connect to OpenAI API: " + e.getMessage());
        } catch (WebClientResponseException e) {
            log.error("HTTP error calling OpenAI API: {} - {}", e.getStatusCode(), e.getMessage());
            throw new AiProviderException("OpenAI API error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error calling OpenAI API", e);
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
        String effectiveModel = (model != null && !model.isBlank()) ? model : "gemini-2.0-flash-exp";
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

