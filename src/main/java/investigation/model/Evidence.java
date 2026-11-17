package investigation.model;

import java.util.LinkedHashMap;
import java.util.Map;

import investigation.util.Json;

/** A clue hidden somewhere in a location (F06). */
public class Evidence {
    private final String id;
    private final String name;
    private final String description;
    private final String locationId;
    private final String hiddenIn;
    private final String pointsToNpcId;
    private final boolean major;
    private boolean found;
    private int foundOnTurn = -1;

    public Evidence(String id, String name, String description, String locationId,
                    String hiddenIn, String pointsToNpcId, boolean major) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.locationId = locationId;
        this.hiddenIn = hiddenIn;
        this.pointsToNpcId = pointsToNpcId;
        this.major = major;
    }

    public String getDetails() {
        return name + " - " + description + " (found in the " + hiddenIn + ")";
    }

    public void markFound(int turn) {
        this.found = true;
        this.foundOnTurn = turn;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getLocationId() { return locationId; }
    public String getHiddenIn() { return hiddenIn; }
    public String getPointsToNpcId() { return pointsToNpcId; }
    public boolean isMajor() { return major; }
    public boolean isFound() { return found; }
    public int getFoundOnTurn() { return foundOnTurn; }

    @Override
    public String toString() {
        return name;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("description", description);
        m.put("locationId", locationId);
        m.put("hiddenIn", hiddenIn);
        m.put("pointsToNpcId", pointsToNpcId);
        m.put("major", major);
        m.put("found", found);
        m.put("foundOnTurn", foundOnTurn);
        return m;
    }

    public static Evidence fromMap(Map<String, Object> m) {
        Evidence e = new Evidence(Json.str(m, "id"), Json.str(m, "name"), Json.str(m, "description", ""),
                Json.str(m, "locationId"), Json.str(m, "hiddenIn", "room"),
                Json.str(m, "pointsToNpcId", null), Json.bool(m, "major", false));
        e.found = Json.bool(m, "found", false);
        e.foundOnTurn = Json.integer(m, "foundOnTurn", -1);
        return e;
    }
}
