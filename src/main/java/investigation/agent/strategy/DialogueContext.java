package investigation.agent.strategy;

import investigation.model.Investigator;
import investigation.model.NPC;

/** Information a dialogue strategy needs to decide what an NPC may reveal. */
public class DialogueContext {
    private final NPC npc;
    private final Investigator player;
    private final String question;
    private final boolean culprit;
    private final long evidenceAgainst;

    public DialogueContext(NPC npc, Investigator player, String question, boolean culprit, long evidenceAgainst) {
        this.npc = npc;
        this.player = player;
        this.question = question;
        this.culprit = culprit;
        this.evidenceAgainst = evidenceAgainst;
    }

    public NPC getNpc() { return npc; }
    public Investigator getPlayer() { return player; }
    public String getQuestion() { return question; }
    public boolean isCulprit() { return culprit; }
    public long getEvidenceAgainst() { return evidenceAgainst; }
}
