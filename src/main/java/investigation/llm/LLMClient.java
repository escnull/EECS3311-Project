package investigation.llm;

/**
 * The only way the rest of the system talks to a language model, so the provider
 * can be swapped (Anthropic Claude, any OpenAI-compatible endpoint, or offline mode).
 */
public interface LLMClient {
    String DEFAULT_SYSTEM = "You are the game-master agent of a detective investigation game. "
            + "Follow the output format you are given exactly. Never invent game rules.";

    String generate(String system, String prompt) throws LLMException;

    default String generate(String prompt) throws LLMException {
        return generate(DEFAULT_SYSTEM, prompt);
    }

    String getModelName();

    TokenUsage getUsage();
}
