package investigation.agent.strategy;

import java.util.ArrayList;
import java.util.List;

import investigation.model.NPC;

/**
 * The NPC dodges questions. It only opens up when the relationship is good or when
 * an Intimidating investigator presses it with evidence. It never confesses its secret.
 */
public class EvasiveStrategy implements AgentStrategy {
    public static final int OPEN_UP_RELATIONSHIP = 2;

    private boolean opensUp(DialogueContext ctx) {
        return ctx.getNpc().getRelationship() >= OPEN_UP_RELATIONSHIP
                || (ctx.getPlayer().has("Intimidating") && ctx.getEvidenceAgainst() >= 1);
    }

    private List<String> allowed(DialogueContext ctx) {
        List<String> facts = ctx.getNpc().getKnownFacts();
        if (opensUp(ctx)) {
            return facts;
        }
        return facts.isEmpty() ? facts : facts.subList(0, 1);
    }

    @Override
    public String execute(DialogueContext ctx) {
        StringBuilder sb = new StringBuilder("BEHAVIOUR: evasive. You are guarded and avoid direct answers.");
        if (opensUp(ctx)) {
            sb.append(" You reluctantly share what you know, but you never admit your secret.");
        } else {
            sb.append(" Deflect, change the subject, or give a vague half-answer. Share at most the one allowed fact.");
        }
        if (ctx.isCulprit() && ctx.getEvidenceAgainst() >= 2) {
            sb.append(" The evidence is closing in on you: sound nervous, but do NOT confess.");
        }
        sb.append("\nALLOWED FACTS:");
        for (String f : allowed(ctx)) {
            sb.append("\n- ").append(f);
        }
        return sb.toString();
    }

    @Override
    public List<String> forbiddenFacts(DialogueContext ctx) {
        List<String> out = new ArrayList<>();
        NPC npc = ctx.getNpc();
        if (!npc.getSecret().isBlank()) {
            out.add(npc.getSecret());
        }
        List<String> ok = allowed(ctx);
        for (String f : npc.getKnownFacts()) {
            if (!ok.contains(f)) {
                out.add(f);
            }
        }
        return out;
    }

    @Override
    public String fallbackLine(DialogueContext ctx) {
        return "I really don't see how that's any of your business, detective.";
    }

    @Override
    public String getName() {
        return "evasive";
    }
}
