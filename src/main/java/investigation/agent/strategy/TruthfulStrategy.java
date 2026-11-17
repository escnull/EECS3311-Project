package investigation.agent.strategy;

import java.util.ArrayList;
import java.util.List;

import investigation.model.NPC;

/** The NPC answers honestly and shares everything it knows (but never a culprit's secret). */
public class TruthfulStrategy implements AgentStrategy {
    @Override
    public String execute(DialogueContext ctx) {
        NPC npc = ctx.getNpc();
        List<String> facts = npc.getKnownFacts();
        StringBuilder sb = new StringBuilder("BEHAVIOUR: truthful. Answer the question honestly and helpfully.");
        if (npc.getRelationship() <= -2) {
            sb.append(" You are annoyed with the investigator, so be curt and share only one fact.");
            facts = facts.isEmpty() ? facts : facts.subList(0, 1);
        }
        sb.append("\nALLOWED FACTS:");
        for (String f : facts) {
            sb.append("\n- ").append(f);
        }
        return sb.toString();
    }

    @Override
    public List<String> forbiddenFacts(DialogueContext ctx) {
        List<String> out = new ArrayList<>();
        if (ctx.isCulprit() && !ctx.getNpc().getSecret().isBlank()) {
            out.add(ctx.getNpc().getSecret());
        }
        return out;
    }

    @Override
    public String fallbackLine(DialogueContext ctx) {
        List<String> facts = ctx.getNpc().getKnownFacts();
        return facts.isEmpty() ? "I'm sorry, I don't know anything that would help."
                : "All I can tell you is this: " + facts.get(0);
    }

    @Override
    public String getName() {
        return "truthful";
    }
}
