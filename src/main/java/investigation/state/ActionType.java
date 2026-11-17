package investigation.state;

/** Categories of player requests that a GameState may allow or forbid. */
public enum ActionType {
    NEW_CASE, MOVE, SEARCH, QUESTION, ACCUSE, SUGGEST_LEADS, VIEW_CASE, SAVE, ABANDON
}
