package investigation.model;

import java.util.LinkedHashMap;
import java.util.Map;

import investigation.util.Json;

/** Immutable record of something that happened, sent from Game to every GameObserver. */
public class GameEvent {
    private final EventType type;
    private final String details;
    private final String subjectId;
    private final int turn;

    public GameEvent(EventType type, String details) {
        this(type, details, null, 0);
    }

    public GameEvent(EventType type, String details, String subjectId, int turn) {
        this.type = type;
        this.details = details == null ? "" : details;
        this.subjectId = subjectId;
        this.turn = turn;
    }

    public EventType getType() { return type; }
    public String getDetails() { return details; }
    public String getSubjectId() { return subjectId; }
    public int getTurn() { return turn; }

    public GameEvent withTurn(int t) {
        return new GameEvent(type, details, subjectId, t);
    }

    @Override
    public String toString() {
        return "[turn " + turn + "] " + type + ": " + details;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type.name());
        m.put("details", details);
        m.put("subjectId", subjectId);
        m.put("turn", turn);
        return m;
    }

    public static GameEvent fromMap(Map<String, Object> m) {
        return new GameEvent(EventType.valueOf(Json.str(m, "type")), Json.str(m, "details", ""),
                Json.str(m, "subjectId", null), Json.integer(m, "turn", 0));
    }
}
