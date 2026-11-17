package investigation.state;

import investigation.model.Game;

/** No investigation is running: only starting (or loading) a case is possible. */
public class NoCaseState implements GameState {
    @Override
    public boolean handleAction(ActionType action) {
        return action == ActionType.NEW_CASE;
    }

    @Override
    public String getRejectionReason(ActionType action) {
        return "There is no active case. Start a new investigation (or load a saved game) first.";
    }

    @Override
    public String getStateName() {
        return "No Case";
    }

    @Override
    public GameState next(Game game) {
        return game.getCase() != null ? new InvestigatingState().next(game) : this;
    }
}
