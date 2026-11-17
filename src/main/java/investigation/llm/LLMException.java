package investigation.llm;

/** Raised when the LLM cannot be reached or returns an unusable response. */
public class LLMException extends Exception {
    public LLMException(String message) {
        super(message);
    }

    public LLMException(String message, Throwable cause) {
        super(message, cause);
    }
}
