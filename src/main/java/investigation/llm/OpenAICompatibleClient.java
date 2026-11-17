package investigation.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import investigation.util.Json;

/**
 * LLMClient for any OpenAI-compatible chat-completions endpoint (OpenAI, Groq, OpenRouter,
 * Google Gemini's OpenAI endpoint, or a local Ollama server). Lets the game run on free models.
 */
public class OpenAICompatibleClient implements LLMClient {
    private final String apiKey;
    private final String model;
    private final String url;
    private final int maxTokens;
    private final Duration timeout;
    private final HttpClient http;
    private final TokenUsage usage = new TokenUsage();

    public OpenAICompatibleClient(String apiKey, String model, String baseUrl, int maxTokens, int timeoutSeconds) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("LLM_BASE_URL must be set for the openai provider");
        }
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.url = baseUrl.replaceAll("/+$", "") + "/chat/completions";
        this.maxTokens = maxTokens;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String generate(String system, String prompt) throws LLMException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("temperature", 0.8);
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", prompt)));
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body)));
        if (!apiKey.isEmpty()) {
            b.header("Authorization", "Bearer " + apiKey);
        }
        HttpResponse<String> response = AIClient.HttpSupport.sendWithRetry(http, b.build(), usage);
        try {
            Map<String, Object> json = (Map<String, Object>) Json.parse(response.body());
            List<Map<String, Object>> choices = Json.objects(json, "choices");
            if (choices.isEmpty() || !(choices.get(0).get("message") instanceof Map)) {
                throw new LLMException("The model returned no choices");
            }
            String text = Json.str((Map<String, Object>) choices.get(0).get("message"), "content", "");
            Object u = json.get("usage");
            if (u instanceof Map) {
                Map<String, Object> um = (Map<String, Object>) u;
                usage.record(Json.integer(um, "prompt_tokens", 0), Json.integer(um, "completion_tokens", 0));
            } else {
                usage.record(0, 0);
            }
            if (text.isBlank()) {
                throw new LLMException("The model returned an empty response");
            }
            return text.trim();
        } catch (RuntimeException e) {
            usage.recordFailure();
            throw new LLMException("Could not read the model response", e);
        }
    }

    @Override
    public String getModelName() {
        return model + " via " + url.replace("/chat/completions", "");
    }

    @Override
    public TokenUsage getUsage() {
        return usage;
    }
}
