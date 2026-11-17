package investigation.state;

import investigation.model.Game;

/**
 * State pattern: what the player may do depends on the phase of the game.
 * Each concrete state also decides which state comes next.
 */
public interface GameState {
    /** True if the action is allowed in this phase. */
    boolean handleAction(ActionType action);

    /** Explanation shown to the player when handleAction returns false. */
    String getRejectionReason(ActionType action);

    String getStateName();

    /** Returns the state the game should be in now (may be this). */
    GameState next(Game game);
}
