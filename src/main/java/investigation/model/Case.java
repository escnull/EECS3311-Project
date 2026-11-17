package investigation.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import investigation.util.Json;

/** The product created by a CaseCreator: the full, validated content of one investigation. */
public class Case {
    public static final int MAX_WRONG_ACCUSATIONS = 2;

    private final String title;
    private final String description;
    private final String crimeType;
    private final String subject;
    private final String culpritId;
    private final String keyEvidenceId;
    private CaseStatus status = CaseStatus.OPEN;
    private final List<Location> locations = new ArrayList<>();
    private final List<NPC> npcs = new ArrayList<>();
    private final List<Evidence> evidence = new ArrayList<>();
    private final List<Lead> leads = new ArrayList<>();
    private final List<Objective> objectives = new ArrayList<>();
    private final Set<String> visitedLocationIds = new LinkedHashSet<>();
    private int wrongAccusations;

    public Case(String title, String description, String crimeType, String subject,
                String culpritId, String keyEvidenceId) {
        this.title = title;
        this.description = description;
        this.crimeType = crimeType;
        this.subject = subject;
        this.culpritId = culpritId;
        this.keyEvidenceId = keyEvidenceId;
    }

    public void addEvidence(Evidence e) { evidence.add(e); }
    public void addSuspect(NPC n) { npcs.add(n); }
    public void addLocation(Location l) { locations.add(l); }
    public void addObjective(Objective o) { objectives.add(o); }

    /** Adds a lead unless an equivalent lead already exists. Returns true if added. */
    public boolean addLead(Lead l) {
        for (Lead existing : leads) {
            if (existing.getDescription().equalsIgnoreCase(l.getDescription())
                    || (l.getNpcId() != null && l.getNpcId().equals(existing.getNpcId())
                        && existing.getEvidenceIds().containsAll(l.getEvidenceIds())
                        && l.getEvidenceIds().containsAll(existing.getEvidenceIds()))) {
                return false;
            }
        }
        leads.add(l);
        return true;
    }

    public void updateStatus(CaseStatus s) { status = s; }

    public void markVisited(String locationId) { visitedLocationIds.add(locationId); }

    public void recordWrongAccusation() { wrongAccusations++; }

    public Location getLocation(String id) {
        return locations.stream().filter(l -> l.getId().equals(id)).findFirst().orElse(null);
    }

    public NPC getNpc(String id) {
        return npcs.stream().filter(n -> n.getId().equals(id)).findFirst().orElse(null);
    }

    public Evidence getEvidence(String id) {
        return evidence.stream().filter(e -> e.getId().equals(id)).findFirst().orElse(null);
    }

    public List<NPC> getNpcsAt(String locationId) {
        return npcs.stream().filter(n -> n.getLocationId().equals(locationId)).collect(Collectors.toList());
    }

    public List<Evidence> getEvidenceAt(String locationId) {
        return evidence.stream().filter(e -> e.getLocationId().equals(locationId)).collect(Collectors.toList());
    }

    public List<Evidence> getFoundEvidence() {
        return evidence.stream().filter(Evidence::isFound).collect(Collectors.toList());
    }

    /** Found evidence that points at the given NPC. */
    public long countFoundEvidenceAgainst(String npcId) {
        return evidence.stream().filter(e -> e.isFound() && npcId.equals(e.getPointsToNpcId())).count();
    }

    public Location getStartLocation() { return locations.isEmpty() ? null : locations.get(0); }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCrimeType() { return crimeType; }
    public String getSubject() { return subject; }
    public String getCulpritId() { return culpritId; }
    public String getKeyEvidenceId() { return keyEvidenceId; }
    public CaseStatus getStatus() { return status; }
    public List<Location> getLocations() { return List.copyOf(locations); }
    public List<NPC> getNpcs() { return List.copyOf(npcs); }
    public List<Evidence> getAllEvidence() { return List.copyOf(evidence); }
    public List<Lead> getLeads() { return List.copyOf(leads); }
    public List<Objective> getObjectives() { return List.copyOf(objectives); }
    public Set<String> getVisitedLocationIds() { return Set.copyOf(visitedLocationIds); }
    public int getWrongAccusations() { return wrongAccusations; }

    public String getBriefing() {
        return title + " (" + crimeType + ")\n" + description;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("description", description);
        m.put("crimeType", crimeType);
        m.put("subject", subject);
        m.put("culpritId", culpritId);
        m.put("keyEvidenceId", keyEvidenceId);
        m.put("status", status.name());
        m.put("wrongAccusations", wrongAccusations);
        m.put("visitedLocationIds", new ArrayList<>(visitedLocationIds));
        m.put("locations", locations.stream().map(Location::toMap).collect(Collectors.toList()));
        m.put("npcs", npcs.stream().map(NPC::toMap).collect(Collectors.toList()));
        m.put("evidence", evidence.stream().map(Evidence::toMap).collect(Collectors.toList()));
        m.put("leads", leads.stream().map(Lead::toMap).collect(Collectors.toList()));
        m.put("objectives", objectives.stream().map(Objective::toMap).collect(Collectors.toList()));
        return m;
    }

    public static Case fromMap(Map<String, Object> m) {
        Case c = new Case(Json.str(m, "title"), Json.str(m, "description", ""), Json.str(m, "crimeType", "murder"),
                Json.str(m, "subject", ""), Json.str(m, "culpritId"), Json.str(m, "keyEvidenceId", null));
        c.status = CaseStatus.valueOf(Json.str(m, "status", "OPEN"));
        c.wrongAccusations = Json.integer(m, "wrongAccusations", 0);
        c.visitedLocationIds.addAll(Json.strings(m, "visitedLocationIds"));
        Json.objects(m, "locations").forEach(x -> c.addLocation(Location.fromMap(x)));
        Json.objects(m, "npcs").forEach(x -> c.addSuspect(NPC.fromMap(x)));
        Json.objects(m, "evidence").forEach(x -> c.addEvidence(Evidence.fromMap(x)));
        Json.objects(m, "leads").forEach(x -> c.leads.add(Lead.fromMap(x)));
        Json.objects(m, "objectives").forEach(x -> c.addObjective(Objective.fromMap(x)));
        return c;
    }
}
