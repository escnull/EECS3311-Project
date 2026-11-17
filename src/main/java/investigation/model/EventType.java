package investigation.model;

/** Kinds of events Game publishes to its observers. */
public enum EventType {
    PLAYER_CREATED(true),
    CASE_STARTED(true),
    MOVED(true),
    NARRATION(true),
    EVIDENCE_FOUND(true),
    DIALOGUE(true),
    LEADS_UPDATED(true),
    OBJECTIVES_UPDATED(false),
    RULE_CHANGES(true),
    CONSEQUENCE(true),
    ACCUSATION(true),
    CASE_SOLVED(true),
    CASE_FAILED(true),
    STATE_CHANGED(false),
    GAME_SAVED(false),
    GAME_LOADED(false),
    ACTION_REJECTED(false),
    MESSAGE(false),
    ERROR(false);

    private final boolean memorable;

    EventType(boolean memorable) {
        this.memorable = memorable;
    }

    /** Whether MemoryManager should keep this event as part of the story history. */
    public boolean isMemorable() {
        return memorable;
    }
}
