package investigation.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import investigation.util.Json;

/** A possible connection between evidence and a person (F07). */
public class Lead {
    public enum Status { OPEN, CONFIRMED, DISMISSED }

    private final String description;
    private final List<String> evidenceIds;
    private final String npcId;
    private final String source;
    private Status status = Status.OPEN;

    public Lead(String description, List<String> evidenceIds, String npcId, String source) {
        this.description = description;
        this.evidenceIds = new ArrayList<>(evidenceIds);
        this.npcId = npcId;
        this.source = source;
    }

    public String getDescription() { return description; }
    public List<String> getEvidenceIds() { return List.copyOf(evidenceIds); }
    public String getNpcId() { return npcId; }
    public String getSource() { return source; }
    public Status getStatus() { return status; }
    public void setStatus(Status s) { status = s; }

    @Override
    public String toString() {
        return "[" + status + "] " + description + " (" + source + ")";
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("description", description);
        m.put("evidenceIds", evidenceIds);
        m.put("npcId", npcId);
        m.put("source", source);
        m.put("status", status.name());
        return m;
    }

    public static Lead fromMap(Map<String, Object> m) {
        Lead l = new Lead(Json.str(m, "description", ""), Json.strings(m, "evidenceIds"),
                Json.str(m, "npcId", null), Json.str(m, "source", "system"));
        l.status = Status.valueOf(Json.str(m, "status", "OPEN"));
        return l;
    }
}
