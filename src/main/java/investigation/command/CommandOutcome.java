package investigation.command;

import java.util.ArrayList;
import java.util.List;

import investigation.model.Location;
import investigation.model.NPC;

/** What happened when a command executed; read by GameController for follow-up AI work. */
public class CommandOutcome {
    private final String message;
    private boolean majorDecision;
    private String decisionSummary;
    private final List<String> deterministicChanges = new ArrayList<>();
    private Location arrivedAt;
    private NPC npcToRespond;
    private String question;
    private int relationshipDelta;

    public CommandOutcome(String message) {
        this.message = message;
    }

    public CommandOutcome major(String summary) {
        this.majorDecision = true;
        this.decisionSummary = summary;
        return this;
    }

    public CommandOutcome change(String c) {
        deterministicChanges.add(c);
        return this;
    }

    public CommandOutcome arrivedAt(Location l) { this.arrivedAt = l; return this; }

    public CommandOutcome respond(NPC npc, String q, int delta) {
        this.npcToRespond = npc;
        this.question = q;
        this.relationshipDelta = delta;
        return this;
    }

    public String getMessage() { return message; }
    public boolean isMajorDecision() { return majorDecision; }
    public String getDecisionSummary() { return decisionSummary; }
    public List<String> getDeterministicChanges() { return deterministicChanges; }
    public Location getArrivedAt() { return arrivedAt; }
    public NPC getNpcToRespond() { return npcToRespond; }
    public String getQuestion() { return question; }
    public int getRelationshipDelta() { return relationshipDelta; }
}
