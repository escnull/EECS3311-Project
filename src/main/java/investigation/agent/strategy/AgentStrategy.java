package investigation.agent.strategy;

import java.util.List;

/**
 * Strategy pattern: how an NPC behaves in conversation. The strategy decides
 * deterministically which facts may be revealed; the LLM only writes the words.
 */
public interface AgentStrategy {
    /** Returns behaviour instructions plus the facts the NPC is allowed to reveal. */
    String execute(DialogueContext context);

    /** Facts the reply must not reveal; used to reject replies that leak them. */
    List<String> forbiddenFacts(DialogueContext context);

    /** Safe line used when the LLM fails or keeps producing invalid replies. */
    String fallbackLine(DialogueContext context);

    String getName();
}
