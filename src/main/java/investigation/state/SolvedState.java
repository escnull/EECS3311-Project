package investigation.state;

import investigation.model.Game;

/** The case is closed (solved or failed). It can be reviewed, saved, or a new one started. */
public class SolvedState implements GameState {
    @Override
    public boolean handleAction(ActionType action) {
        return action == ActionType.NEW_CASE || action == ActionType.VIEW_CASE || action == ActionType.SAVE;
    }

    @Override
    public String getRejectionReason(ActionType action) {
        return "This case is closed. You can review the case file or start a new investigation.";
    }

    @Override
    public String getStateName() {
        return "Case Closed";
    }

    @Override
    public GameState next(Game game) {
        return game.getCase() == null ? new NoCaseState() : this;
    }
}
