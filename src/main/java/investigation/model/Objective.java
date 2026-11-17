package investigation.model;

import java.util.LinkedHashMap;
import java.util.Map;

import investigation.util.Json;

/** A case objective whose completion is checked deterministically against the game (F11). */
public class Objective {
    public enum Type { VISIT_LOCATION, FIND_EVIDENCE, FIND_EVIDENCE_COUNT, QUESTION_NPC, SOLVE_CASE }
    public enum Status { ACTIVE, COMPLETED, UNAVAILABLE }

    private final String description;
    private final Type type;
    private final String targetId;
    private final int count;
    private final String source;
    private Status status = Status.ACTIVE;

    public Objective(String description, Type type, String targetId, int count, String source) {
        this.description = description;
        this.type = type;
        this.targetId = targetId;
        this.count = count;
        this.source = source;
    }

    /**
     * Re-evaluates the objective. Returns true if its status changed.
     */
    public boolean evaluate(Case c) {
        if (status != Status.ACTIVE) {
            return false;
        }
        Status next = Status.ACTIVE;
        switch (type) {
            case VISIT_LOCATION:
                if (c.getVisitedLocationIds().contains(targetId)) {
                    next = Status.COMPLETED;
                }
                break;
            case FIND_EVIDENCE:
                Evidence e = c.getEvidence(targetId);
                if (e != null && e.isFound()) {
                    next = Status.COMPLETED;
                }
                break;
            case FIND_EVIDENCE_COUNT:
                if (c.getFoundEvidence().size() >= count) {
                    next = Status.COMPLETED;
                }
                break;
            case QUESTION_NPC:
                NPC npc = c.getNpc(targetId);
                if (npc != null && npc.getInterviewCount() >= count) {
                    next = Status.COMPLETED;
                } else if (npc == null || npc.refusesToTalk()) {
                    next = Status.UNAVAILABLE;
                }
                break;
            case SOLVE_CASE:
                if (c.getStatus() == CaseStatus.SOLVED) {
                    next = Status.COMPLETED;
                } else if (c.getStatus() == CaseStatus.FAILED) {
                    next = Status.UNAVAILABLE;
                }
                break;
            default:
                break;
        }
        if (next != status) {
            status = next;
            return true;
        }
        return false;
    }

    public String getDescription() { return description; }
    public Type getType() { return type; }
    public String getTargetId() { return targetId; }
    public int getCount() { return count; }
    public String getSource() { return source; }
    public Status getStatus() { return status; }
    public boolean isCompleted() { return status == Status.COMPLETED; }

    @Override
    public String toString() {
        String box = status == Status.COMPLETED ? "[x]" : status == Status.UNAVAILABLE ? "[-]" : "[ ]";
        return box + " " + description + ("agent".equals(source) ? "  (suggested by agent)" : "");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("description", description);
        m.put("type", type.name());
        m.put("targetId", targetId);
        m.put("count", count);
        m.put("source", source);
        m.put("status", status.name());
        return m;
    }

    public static Objective fromMap(Map<String, Object> m) {
        Objective o = new Objective(Json.str(m, "description", ""), Type.valueOf(Json.str(m, "type")),
                Json.str(m, "targetId", null), Json.integer(m, "count", 1), Json.str(m, "source", "system"));
        o.status = Status.valueOf(Json.str(m, "status", "ACTIVE"));
        return o;
    }
}
