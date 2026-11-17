package investigation.llm;

/** Running totals of LLM usage, shown in the GUI status bar and the CLI 'usage' command. */
public class TokenUsage {
    private int calls;
    private long inputTokens;
    private long outputTokens;
    private int failures;

    public synchronized void record(long in, long out) {
        calls++;
        inputTokens += in;
        outputTokens += out;
    }

    public synchronized void recordFailure() {
        failures++;
    }

    public synchronized int getCalls() { return calls; }
    public synchronized long getInputTokens() { return inputTokens; }
    public synchronized long getOutputTokens() { return outputTokens; }
    public synchronized int getFailures() { return failures; }

    @Override
    public synchronized String toString() {
        return calls + " LLM calls, " + inputTokens + " input / " + outputTokens + " output tokens"
                + (failures > 0 ? ", " + failures + " failed" : "");
    }
}
