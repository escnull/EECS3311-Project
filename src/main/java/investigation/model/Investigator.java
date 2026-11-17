package investigation.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import investigation.util.Json;

/** The player's investigator profile (F01). */
public class Investigator {
    /** Attributes a player may choose from; each one changes gameplay slightly. */
    public static final List<String> ALLOWED_ATTRIBUTES = List.of(
            "Perceptive", "Charismatic", "Intimidating", "Methodical");
    public static final int MAX_ATTRIBUTES = 2;

    private final String name;
    private final List<String> attributes;
    private int reputation;

    public Investigator(String name, List<String> attributes) {
        this.name = name;
        this.attributes = new ArrayList<>(attributes);
    }

    /** Returns null when the input is valid, otherwise a message describing what is wrong. */
    public static String validate(String name, List<String> attributes) {
        if (name == null || name.trim().isEmpty()) {
            return "Investigator name cannot be empty.";
        }
        if (name.trim().length() > 30) {
            return "Investigator name must be 30 characters or fewer.";
        }
        if (!name.trim().matches("[A-Za-z][A-Za-z .'-]*")) {
            return "Name may only contain letters, spaces, apostrophes, dots and hyphens.";
        }
        if (attributes == null || attributes.isEmpty()) {
            return "Choose at least one attribute: " + ALLOWED_ATTRIBUTES;
        }
        if (attributes.size() > MAX_ATTRIBUTES) {
            return "Choose at most " + MAX_ATTRIBUTES + " attributes.";
        }
        for (String a : attributes) {
            if (normalise(a) == null) {
                return "Unknown attribute '" + a + "'. Allowed: " + ALLOWED_ATTRIBUTES;
            }
        }
        if (attributes.stream().map(Investigator::normalise).distinct().count() != attributes.size()) {
            return "Attributes must be different from each other.";
        }
        return null;
    }

    /** Maps user input such as "perceptive" to the canonical attribute name, or null. */
    public static String normalise(String attr) {
        if (attr == null) {
            return null;
        }
        for (String a : ALLOWED_ATTRIBUTES) {
            if (a.equalsIgnoreCase(attr.trim())) {
                return a;
            }
        }
        return null;
    }

    public boolean has(String attribute) {
        return attributes.contains(attribute);
    }

    public String getName() { return name; }
    public List<String> getAttributes() { return List.copyOf(attributes); }
    public int getReputation() { return reputation; }

    public void updateReputation(int value) {
        reputation = Math.max(-10, Math.min(10, reputation + value));
    }

    public String getInfo() {
        return "Investigator " + name + "\nAttributes: " + String.join(", ", attributes)
                + "\nReputation: " + reputation;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("attributes", attributes);
        m.put("reputation", reputation);
        return m;
    }

    public static Investigator fromMap(Map<String, Object> m) {
        String name = Json.str(m, "name");
        List<String> attrs = Json.strings(m, "attributes");
        if (validate(name, attrs) != null) {
            throw new IllegalArgumentException("Invalid investigator in save file: " + validate(name, attrs));
        }
        Investigator i = new Investigator(name.trim(), attrs);
        i.reputation = Json.integer(m, "reputation", 0);
        return i;
    }
}
