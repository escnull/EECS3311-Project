package investigation.llm;

import investigation.config.AppConfig;

/** Builds the configured LLMClient. */
public final class LLMClientFactory {
    private LLMClientFactory() { }

    public static LLMClient create(AppConfig config) {
        String provider = config.provider();
        int maxTokens = config.getInt("LLM_MAX_TOKENS", 2500);
        int timeout = config.getInt("LLM_TIMEOUT_SECONDS", 90);
        switch (provider) {
            case "offline":
                return new OfflineLLMClient();
            case "openai":
                return new OpenAICompatibleClient(config.get("LLM_API_KEY", ""),
                        config.get("LLM_MODEL", "gpt-4o-mini"), config.get("LLM_BASE_URL", null), maxTokens, timeout);
            case "anthropic":
            default:
                if (!config.hasRealKey()) {
                    throw new IllegalStateException("No LLM API key configured. Copy .env.example to .env and "
                            + "set LLM_API_KEY, or start with --offline to test without an LLM.");
                }
                return new AIClient(config.get("LLM_API_KEY", ""), config.get("LLM_MODEL", "claude-haiku-4-5"),
                        config.get("LLM_BASE_URL", null), maxTokens, timeout);
        }
    }
}
