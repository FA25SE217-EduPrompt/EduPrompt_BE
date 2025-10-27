package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.enums.AiModel;
import SEP490.EduPrompt.exception.client.AiProviderException;
import SEP490.EduPrompt.model.Prompt;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AiClientServiceImpl implements AiClientService {

    private static final int TIMEOUT_SECONDS = 30;
    private final WebClient webClient;
    @Value("${openai.api.key}")
    private String openaiApiKey;
    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String openaiApiUrl;

    public AiClientServiceImpl(WebClient.Builder webClientBuilder) {
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
    }

    @Override
    public String testPrompt(Prompt prompt, String aiModel, String inputText,
                             Double temperature, Integer maxTokens, Double topP) {
        log.info("Testing prompt {} with model: {}", prompt.getId(), aiModel);

        String fullPrompt = buildFullPrompt(prompt, inputText);
        return callOpenAiApi(fullPrompt, aiModel, temperature, maxTokens, topP);
    }

    @Override
    public String optimizePrompt(Prompt prompt, String optimizationInput,
                                 Double temperature, Integer maxTokens) {
        log.info("Optimizing prompt {}", prompt.getId());

        String optimizationPrompt = buildOptimizationPrompt(prompt, optimizationInput);
        return callOpenAiApi(optimizationPrompt, AiModel.GPT_4O_MINI.getName(), temperature, maxTokens, 1.0);
    }

    private String callOpenAiApi(String prompt, String model, Double temperature,
                                 Integer maxTokens, Double topP) {
        try {
            log.debug("Calling OpenAI API with model: {}", model);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(
                    Map.of("role", "user", "content", prompt)
            ));

            if (temperature != null) {
                requestBody.put("temperature", temperature);
            }
            if (maxTokens != null) {
                requestBody.put("max_tokens", maxTokens);
            }
            if (topP != null) {
                requestBody.put("top_p", topP);
            }

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

            if (responseBody != null) {
                //follow the openai response
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");

                if (choices != null && !choices.isEmpty()) {
                    //get the first choice
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");

                    log.info("OpenAI API call successful");
                    return content;
                }
            }

            throw new AiProviderException("Invalid response from OpenAI API");

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

