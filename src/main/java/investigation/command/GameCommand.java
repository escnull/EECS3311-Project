package investigation.command;

import investigation.model.Game;
import investigation.state.ActionType;

/**
 * Command pattern: every player or AI request becomes a command object that is validated
 * against the rules before it may change the Game (the receiver).
 */
public interface GameCommand {
    /** Checks the current GameState and the command's own rules. Never changes the game. */
    boolean validate(Game game);

    /** Applies the action. Only called after validate returned true. */
    CommandOutcome execute(Game game);

    /** Reason for the last failed validate call. */
    String getRejectionReason();

    ActionType getActionType();

    /** Short human-readable description, e.g. "search the desk". */
    String describe();
}
