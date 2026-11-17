package investigation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import investigation.llm.LLMClient;
import investigation.llm.LLMException;
import investigation.llm.TokenUsage;

/** Test LLM that returns pre-scripted replies in order and records the prompts it received. */
public class ScriptedLLM implements LLMClient {
    private final Deque<String> replies = new ArrayDeque<>();
    public final List<String> prompts = new ArrayList<>();
    private final TokenUsage usage = new TokenUsage();

    public ScriptedLLM(String... replies) {
        for (String r : replies) {
            this.replies.add(r);
        }
    }

    @Override
    public String generate(String system, String prompt) throws LLMException {
        prompts.add(prompt);
        if (replies.isEmpty()) {
            throw new LLMException("no scripted reply left");
        }
        String r = replies.size() == 1 ? replies.peek() : replies.poll();
        usage.record(1, 1);
        return r;
    }

    @Override
    public String getModelName() {
        return "scripted";
    }

    @Override
    public TokenUsage getUsage() {
        return usage;
    }
}
