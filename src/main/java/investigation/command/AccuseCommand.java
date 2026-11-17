package investigation.command;

import investigation.model.Case;
import investigation.model.CaseStatus;
import investigation.model.EventType;
import investigation.model.Game;
import investigation.model.Lead;
import investigation.model.NPC;
import investigation.state.ActionType;

/** New in Stage 2 (F13): formally accuse a suspect, which decides how the case ends. */
public class AccuseCommand extends AbstractCommand {
    public static final int REQUIRED_EVIDENCE = 2;

    private final String npcId;

    public AccuseCommand(String npcId) {
        this.npcId = npcId;
    }

    @Override
    protected String checkRules(Game game) {
        if (game.getCase().getNpc(npcId) == null) {
            return "There is nobody called '" + npcId + "' to accuse.";
        }
        if (game.getCase().getFoundEvidence().isEmpty()) {
            return "You have no evidence yet. Find something before accusing anyone.";
        }
        return null;
    }

    @Override
    public CommandOutcome execute(Game game) {
        Case c = game.getCase();
        NPC accused = c.getNpc(npcId);
        boolean correct = npcId.equals(c.getCulpritId());
        long support = c.countFoundEvidenceAgainst(npcId);
        game.notifyObservers(EventType.ACCUSATION, "You accused " + accused.getName(), npcId);

        if (correct && support >= REQUIRED_EVIDENCE) {
            c.updateStatus(CaseStatus.SOLVED);
            game.getPlayer().updateReputation(3);
            for (Lead l : c.getLeads()) {
                l.setStatus(npcId.equals(l.getNpcId()) ? Lead.Status.CONFIRMED : Lead.Status.DISMISSED);
            }
            game.notifyObservers(EventType.CASE_SOLVED, accused.getName() + " was the culprit. Case solved.", npcId);
            return new CommandOutcome("Your accusation holds. " + accused.getName()
                    + " is taken into custody. CASE SOLVED.")
                    .major("The investigator correctly accused " + accused.getName() + " and solved the case")
                    .change("Case status: SOLVED").change("Reputation +3");
        }

        c.recordWrongAccusation();
        String why;
        CommandOutcome out;
        if (correct) {
            game.getPlayer().updateReputation(-1);
            accused.updateRelationship(-2);
            why = "Without at least " + REQUIRED_EVIDENCE + " pieces of evidence against " + accused.getName()
                    + ", the accusation does not hold.";
            out = new CommandOutcome(why).change("Reputation -1").change(accused.getName() + " is now on guard");
        } else {
            game.getPlayer().updateReputation(-2);
            accused.updateRelationship(-3);
            accused.setRefusesToTalk(true);
            why = accused.getName() + " has an answer for everything. The accusation collapses.";
            out = new CommandOutcome(why).change("Reputation -2")
                    .change(accused.getName() + " refuses to talk to you again");
        }
        int left = Case.MAX_WRONG_ACCUSATIONS - c.getWrongAccusations();
        if (left <= 0) {
            c.updateStatus(CaseStatus.FAILED);
            out.change("Case status: FAILED (too many failed accusations)");
            game.notifyObservers(EventType.CASE_FAILED, "The case was taken away from you after "
                    + c.getWrongAccusations() + " failed accusations.");
        } else {
            out.change("Failed accusations remaining before the case is closed: " + left);
        }
        return out.major("The investigator accused " + accused.getName() + " but the accusation failed");
    }

    @Override
    public ActionType getActionType() {
        return ActionType.ACCUSE;
    }

    @Override
    public String describe() {
        return "accuse " + npcId;
    }

    public String getNpcId() {
        return npcId;
    }
}
