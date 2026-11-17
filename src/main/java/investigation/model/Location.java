package investigation.model;

import java.util.LinkedHashMap;
import java.util.Map;

import investigation.util.Json;

/** A place in the case world. Access rules are deterministic (F03, F08). */
public class Location {
    private final String id;
    private final String name;
    private final String description;
    private final boolean restricted;
    private final String unlockEvidenceId;
    private final int requiredReputation;

    public Location(String id, String name, String description, boolean restricted,
                    String unlockEvidenceId, int requiredReputation) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.restricted = restricted;
        this.unlockEvidenceId = unlockEvidenceId;
        this.requiredReputation = requiredReputation;
    }

    /**
     * A restricted location can be entered once its unlock evidence has been found,
     * or when the investigator's reputation is high enough to be let in.
     */
    public boolean canAccess(Investigator player, Case currentCase) {
        if (!restricted) {
            return true;
        }
        if (unlockEvidenceId != null && currentCase != null) {
            Evidence key = currentCase.getEvidence(unlockEvidenceId);
            if (key != null && key.isFound()) {
                return true;
            }
        }
        return player != null && player.getReputation() >= requiredReputation;
    }

    public String getAccessDeniedReason(Case currentCase) {
        String keyName = "the right evidence";
        if (unlockEvidenceId != null && currentCase != null && currentCase.getEvidence(unlockEvidenceId) != null) {
            keyName = "a clue that gets you in (hint: keep searching other locations)";
        }
        return name + " is restricted. You need " + keyName
                + " or a reputation of at least " + requiredReputation + ".";
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isRestricted() { return restricted; }
    public String getUnlockEvidenceId() { return unlockEvidenceId; }
    public int getRequiredReputation() { return requiredReputation; }

    @Override
    public String toString() {
        return name;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("description", description);
        m.put("restricted", restricted);
        m.put("unlockEvidenceId", unlockEvidenceId);
        m.put("requiredReputation", requiredReputation);
        return m;
    }

    public static Location fromMap(Map<String, Object> m) {
        return new Location(Json.str(m, "id"), Json.str(m, "name"), Json.str(m, "description", ""),
                Json.bool(m, "restricted", false), Json.str(m, "unlockEvidenceId", null),
                Json.integer(m, "requiredReputation", 3));
    }
}
