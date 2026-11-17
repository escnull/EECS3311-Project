package investigation.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import investigation.agent.strategy.AgentStrategy;
import investigation.agent.strategy.EvasiveStrategy;
import investigation.agent.strategy.TruthfulStrategy;
import investigation.util.Json;

/** A suspect or witness. Its dialogue behaviour is a swappable AgentStrategy (Strategy pattern). */
public class NPC {
    public static final int MIN_RELATIONSHIP = -5;
    public static final int MAX_RELATIONSHIP = 5;

    private final String id;
    private final String name;
    private final String role;
    private final String personality;
    private final String locationId;
    private final List<String> knownFacts;
    private final String secret;
    private int relationship;
    private int interviewCount;
    private boolean refusesToTalk;
    private AgentStrategy strategy;

    public NPC(String id, String name, String role, String personality, String locationId,
               List<String> knownFacts, String secret, AgentStrategy strategy) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.personality = personality;
        this.locationId = locationId;
        this.knownFacts = new ArrayList<>(knownFacts);
        this.secret = secret;
        this.strategy = strategy;
    }

    public static AgentStrategy strategyFor(String behaviour) {
        return "evasive".equalsIgnoreCase(behaviour) ? new EvasiveStrategy() : new TruthfulStrategy();
    }

    public void updateRelationship(int value) {
        relationship = Math.max(MIN_RELATIONSHIP, Math.min(MAX_RELATIONSHIP, relationship + value));
    }

    public void recordInterview() {
        interviewCount++;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public String getPersonality() { return personality; }
    public String getLocationId() { return locationId; }
    public List<String> getKnownFacts() { return List.copyOf(knownFacts); }
    public String getSecret() { return secret; }
    public int getRelationship() { return relationship; }
    public int getInterviewCount() { return interviewCount; }
    public boolean refusesToTalk() { return refusesToTalk; }
    public void setRefusesToTalk(boolean r) { refusesToTalk = r; }
    public AgentStrategy getStrategy() { return strategy; }
    public void setStrategy(AgentStrategy s) { strategy = s; }

    @Override
    public String toString() {
        return name + " (" + role + ")";
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("role", role);
        m.put("personality", personality);
        m.put("locationId", locationId);
        m.put("behaviour", strategy.getName());
        m.put("knownFacts", knownFacts);
        m.put("secret", secret);
        m.put("relationship", relationship);
        m.put("interviewCount", interviewCount);
        m.put("refusesToTalk", refusesToTalk);
        return m;
    }

    public static NPC fromMap(Map<String, Object> m) {
        NPC n = new NPC(Json.str(m, "id"), Json.str(m, "name"), Json.str(m, "role", "witness"),
                Json.str(m, "personality", "reserved"), Json.str(m, "locationId"),
                Json.strings(m, "knownFacts"), Json.str(m, "secret", ""),
                strategyFor(Json.str(m, "behaviour", "truthful")));
        n.relationship = Json.integer(m, "relationship", 0);
        n.interviewCount = Json.integer(m, "interviewCount", 0);
        n.refusesToTalk = Json.bool(m, "refusesToTalk", false);
        return n;
    }
}
