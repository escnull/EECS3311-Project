package investigation.command;

import investigation.model.Game;

/**
 * Shared validation: first ask the current GameState whether this kind of action is allowed
 * in the current phase, then check the command-specific rules.
 */
public abstract class AbstractCommand implements GameCommand {
    private String rejectionReason;

    @Override
    public final boolean validate(Game game) {
        rejectionReason = null;
        if (!game.getState().handleAction(getActionType())) {
            rejectionReason = game.getState().getRejectionReason(getActionType());
            return false;
        }
        if (game.getPlayer() == null) {
            rejectionReason = "Create an investigator first.";
            return false;
        }
        String problem = checkRules(game);
        if (problem != null) {
            rejectionReason = problem;
            return false;
        }
        return true;
    }

    /** Returns null if the rules allow the action, otherwise the reason it is not allowed. */
    protected abstract String checkRules(Game game);

    @Override
    public String getRejectionReason() {
        return rejectionReason;
    }
}
