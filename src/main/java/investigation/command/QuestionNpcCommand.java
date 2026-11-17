package investigation.command;

import java.util.Locale;

import investigation.model.EventType;
import investigation.model.Game;
import investigation.model.NPC;
import investigation.state.ActionType;

/** Question an NPC at the current location (F05). The reply itself is written by the agent. */
public class QuestionNpcCommand extends AbstractCommand {
    private static final String[] ACCUSATORY = {
        "you killed", "you stole", "you did it", "liar", "you're lying", "you are lying", "murderer", "thief", "confess"
    };

    private final String npcId;
    private final String question;

    public QuestionNpcCommand(String npcId, String question) {
        this.npcId = npcId;
        this.question = question == null ? "" : question.trim();
    }

    @Override
    protected String checkRules(Game game) {
        NPC npc = game.getCase().getNpc(npcId);
        if (npc == null) {
            return "There is nobody called '" + npcId + "' in this case.";
        }
        if (!npc.getLocationId().equals(game.getCurrentLocation().getId())) {
            return npc.getName() + " is not here. They are at "
                    + game.getCase().getLocation(npc.getLocationId()).getName() + ".";
        }
        if (npc.refusesToTalk()) {
            return npc.getName() + " refuses to speak with you any more.";
        }
        if (question.isEmpty()) {
            return "What do you want to ask " + npc.getName() + "?";
        }
        return null;
    }

    @Override
    public CommandOutcome execute(Game game) {
        NPC npc = game.getCase().getNpc(npcId);
        npc.recordInterview();
        game.notifyObservers(EventType.DIALOGUE, "You asked " + npc.getName() + ": \"" + question + "\"", npcId);
        return new CommandOutcome(null).respond(npc, question, relationshipDelta(game, npc));
    }

    /** Deterministic relationship change based on tone and the investigator's attributes. */
    int relationshipDelta(Game game, NPC npc) {
        String q = question.toLowerCase(Locale.ROOT);
        boolean accusatory = false;
        for (String a : ACCUSATORY) {
            if (q.contains(a)) {
                accusatory = true;
                break;
            }
        }
        int delta;
        if (accusatory) {
            delta = game.getPlayer().has("Intimidating") ? -1 : -2;
        } else {
            delta = q.contains("please") || q.contains("thank") ? 1 : 0;
            if (game.getPlayer().has("Charismatic")) {
                delta += 1;
            }
        }
        return delta;
    }

    @Override
    public ActionType getActionType() {
        return ActionType.QUESTION;
    }

    @Override
    public String describe() {
        return "ask " + npcId + " \"" + question + "\"";
    }

    public String getNpcId() { return npcId; }
    public String getQuestion() { return question; }
}
