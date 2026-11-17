package investigation.casegen;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import investigation.llm.LLMClient;
import investigation.llm.LLMException;
import investigation.model.Case;
import investigation.model.Evidence;
import investigation.model.Location;
import investigation.model.NPC;
import investigation.model.Objective;
import investigation.util.Json;

/**
 * Factory Method creator. Each subclass creates a Case for one crime type; the rest of
 * the game only depends on the Case product. Shared steps (asking the LLM, parsing,
 * validating, retrying) live here.
 */
public abstract class CaseCreator {
    public static final int MAX_ATTEMPTS = 3;

    /** The factory method. */
    public abstract Case createCase(LLMClient llm, String preferences) throws CaseGenerationException;

    public abstract String getCrimeType();

    protected static final String SCHEMA =
            "Respond with JSON only, matching this shape exactly:\n"
            + "{\n"
            + " \"title\": \"short case title\",\n"
            + " \"description\": \"2-3 sentence briefing for the investigator\",\n"
            + " \"subject\": \"the victim's name or the stolen item\",\n"
            + " \"culpritId\": \"id of the guilty person\",\n"
            + " \"keyEvidenceId\": \"id of the evidence described in the special rule\",\n"
            + " \"locations\": [{\"id\": \"snake_case\", \"name\": \"\", \"description\": \"1-2 sentences\", "
            + "\"restricted\": false, \"unlockEvidenceId\": null, \"requiredReputation\": 3}],\n"
            + " \"npcs\": [{\"id\": \"snake_case\", \"name\": \"Full Name\", \"role\": \"\", \"personality\": \"\", "
            + "\"locationId\": \"\", \"behaviour\": \"truthful|evasive\", \"knownFacts\": [\"\"], \"secret\": \"\"}],\n"
            + " \"evidence\": [{\"id\": \"snake_case\", \"name\": \"\", \"description\": \"\", \"locationId\": \"\", "
            + "\"hiddenIn\": \"1-2 word object, e.g. desk\", \"pointsToNpcId\": null, \"major\": false}]\n"
            + "}\n"
            + "General rules:\n"
            + "- 4 or 5 locations. The FIRST location is the crime scene and is not restricted.\n"
            + "- Exactly one location is restricted; its unlockEvidenceId is a piece of evidence found in an "
            + "unrestricted location (for example a key or keycard).\n"
            + "- 4 people: 3 suspects and 1 witness. The culprit is a suspect with behaviour \"evasive\". "
            + "At least one innocent person is also evasive (they hide an unrelated secret).\n"
            + "- Each person has 2-3 knownFacts (things they would truthfully say) and a secret. "
            + "The culprit's secret is what they actually did.\n"
            + "- 6 or 7 pieces of evidence. At least 3 point to the culprit (pointsToNpcId) and are major; "
            + "one red herring points to an innocent suspect. Spread evidence across locations; "
            + "no more than 3 in one location. hiddenIn objects in the same location must differ.\n"
            + "- The case must be solvable: facts and evidence together point to the culprit.";

    /** Asks the LLM for a case until it passes validation (up to MAX_ATTEMPTS). */
    protected Case generateValidated(LLMClient llm, String prompt) throws CaseGenerationException {
        String current = prompt;
        String lastProblem = "unknown";
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String reply;
            try {
                reply = llm.generate(current);
            } catch (LLMException e) {
                throw new CaseGenerationException("The LLM could not generate a case: " + e.getMessage());
            }
            try {
                Case c = parse(Json.extractObject(reply));
                String problem = CaseValidator.validate(c);
                if (problem == null) {
                    return c;
                }
                lastProblem = problem;
            } catch (RuntimeException e) {
                lastProblem = "invalid JSON (" + e.getMessage() + ")";
            }
            current = prompt + "\n\nYour previous case was rejected because: " + lastProblem
                    + ". Generate a corrected case that follows every rule.";
        }
        throw new CaseGenerationException("Could not generate a consistent case after " + MAX_ATTEMPTS
                + " attempts (last problem: " + lastProblem + ").");
    }

    /** Converts the LLM JSON into the Case product. */
    protected Case parse(Map<String, Object> m) {
        Case c = new Case(Json.str(m, "title", "Untitled case"), Json.str(m, "description", ""), getCrimeType(),
                Json.str(m, "subject", ""), id(Json.str(m, "culpritId")), id(Json.str(m, "keyEvidenceId")));
        for (Map<String, Object> l : Json.objects(m, "locations")) {
            boolean restricted = Json.bool(l, "restricted", false);
            c.addLocation(new Location(id(Json.str(l, "id")), Json.str(l, "name", "Unnamed place"),
                    Json.str(l, "description", ""), restricted, id(Json.str(l, "unlockEvidenceId")),
                    Math.max(1, Json.integer(l, "requiredReputation", 3))));
        }
        for (Map<String, Object> n : Json.objects(m, "npcs")) {
            c.addSuspect(new NPC(id(Json.str(n, "id")), Json.str(n, "name", "Unknown"), Json.str(n, "role", "witness"),
                    Json.str(n, "personality", "reserved"), id(Json.str(n, "locationId")),
                    Json.strings(n, "knownFacts"), Json.str(n, "secret", ""),
                    NPC.strategyFor(Json.str(n, "behaviour", "truthful"))));
        }
        for (Map<String, Object> e : Json.objects(m, "evidence")) {
            c.addEvidence(new Evidence(id(Json.str(e, "id")), Json.str(e, "name", "Clue"),
                    Json.str(e, "description", ""), id(Json.str(e, "locationId")),
                    Json.str(e, "hiddenIn", "").trim().toLowerCase(Locale.ROOT), id(Json.str(e, "pointsToNpcId")),
                    Json.bool(e, "major", false)));
        }
        return c;
    }

    /** Objectives every case shares; subclasses add crime-specific ones. */
    protected void addCommonObjectives(Case c) {
        c.addObjective(new Objective("Examine the crime scene: " + c.getStartLocation().getName(),
                Objective.Type.VISIT_LOCATION, c.getStartLocation().getId(), 1, "system"));
        c.addObjective(new Objective("Collect at least 3 pieces of evidence",
                Objective.Type.FIND_EVIDENCE_COUNT, null, 3, "system"));
        List<NPC> people = c.getNpcs();
        for (NPC n : people) {
            c.addObjective(new Objective("Question " + n.getName() + " (" + n.getRole() + ")",
                    Objective.Type.QUESTION_NPC, n.getId(), 1, "system"));
        }
    }

    protected static String preferenceLine(String preferences) {
        return preferences == null || preferences.isBlank() ? "Player preferences: none (surprise them)."
                : "Player preferences (follow them if they fit the rules): " + preferences;
    }

    static String id(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("null")) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }
}
