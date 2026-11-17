package investigation.state;

import investigation.model.CaseStatus;
import investigation.model.Game;

/** A case is open: exploration, searching, questioning and accusing are allowed. */
public class InvestigatingState implements GameState {
    @Override
    public boolean handleAction(ActionType action) {
        return action != ActionType.NEW_CASE;
    }

    @Override
    public String getRejectionReason(ActionType action) {
        return "A case is already in progress. Solve it, or use 'abandon' to close it before starting another.";
    }

    @Override
    public String getStateName() {
        return "Investigating";
    }

    @Override
    public GameState next(Game game) {
        if (game.getCase() == null) {
            return new NoCaseState();
        }
        return game.getCase().getStatus() == CaseStatus.OPEN ? this : new SolvedState();
    }
}
