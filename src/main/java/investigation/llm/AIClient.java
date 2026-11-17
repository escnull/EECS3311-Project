package investigation.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import investigation.util.Json;

/** LLMClient for Anthropic's Claude Messages API. The API key comes from local config only. */
public class AIClient implements LLMClient {
    private static final String DEFAULT_URL = "https://api.anthropic.com/v1/messages";

    private final String apiKey;
    private final String model;
    private final String url;
    private final int maxTokens;
    private final Duration timeout;
    private final HttpClient http;
    private final TokenUsage usage = new TokenUsage();

    public AIClient(String apiKey, String model, String baseUrl, int maxTokens, int timeoutSeconds) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("LLM_API_KEY is not set");
        }
        this.apiKey = apiKey.trim();
        this.model = model;
        this.url = baseUrl == null || baseUrl.isBlank() ? DEFAULT_URL : baseUrl.replaceAll("/+$", "") + "/v1/messages";
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
        body.put("system", system);
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body)))
                .build();

        HttpResponse<String> response = HttpSupport.sendWithRetry(http, request, usage);
        try {
            Map<String, Object> json = (Map<String, Object>) Json.parse(response.body());
            StringBuilder text = new StringBuilder();
            for (Map<String, Object> block : Json.objects(json, "content")) {
                if ("text".equals(block.get("type"))) {
                    text.append(Json.str(block, "text", ""));
                }
            }
            Object u = json.get("usage");
            if (u instanceof Map) {
                Map<String, Object> um = (Map<String, Object>) u;
                usage.record(Json.integer(um, "input_tokens", 0), Json.integer(um, "output_tokens", 0));
            } else {
                usage.record(0, 0);
            }
            if (text.length() == 0) {
                throw new LLMException("The model returned an empty response");
            }
            return text.toString().trim();
        } catch (RuntimeException e) {
            usage.recordFailure();
            throw new LLMException("Could not read the model response", e);
        }
    }

    @Override
    public String getModelName() {
        return "Anthropic " + model;
    }

    @Override
    public TokenUsage getUsage() {
        return usage;
    }

    /** Shared HTTP helper for both real clients. */
    static final class HttpSupport {
        private HttpSupport() { }

        @SuppressWarnings("unchecked")
        static HttpResponse<String> sendWithRetry(HttpClient http, HttpRequest req, TokenUsage usage)
                throws LLMException {
            int attempts = 0;
            while (true) {
                attempts++;
                try {
                    HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
                    int code = r.statusCode();
                    if (code / 100 == 2) {
                        return r;
                    }
                    boolean retryable = code == 429 || code >= 500;
                    if (retryable && attempts < 3) {
                        Thread.sleep(1500L * attempts);
                        continue;
                    }
                    usage.recordFailure();
                    String detail = "";
                    try {
                        Object parsed = Json.parse(r.body());
                        if (parsed instanceof Map && ((Map<String, Object>) parsed).get("error") instanceof Map) {
                            detail = ": " + Json.str((Map<String, Object>) ((Map<String, Object>) parsed).get("error"),
                                    "message", "");
                        }
                    } catch (RuntimeException ignored) {
                        // body was not JSON
                    }
                    throw new LLMException("LLM request failed with HTTP " + code + detail);
                } catch (IOException e) {
                    if (attempts < 2) {
                        continue;
                    }
                    usage.recordFailure();
                    throw new LLMException("Could not reach the LLM service (" + e.getClass().getSimpleName() + ")", e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new LLMException("LLM request interrupted", e);
                }
            }
        }
    }
}
