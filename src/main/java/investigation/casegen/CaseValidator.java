package investigation.casegen;

import java.util.HashSet;
import java.util.Set;

import investigation.model.Case;
import investigation.model.Evidence;
import investigation.model.Location;
import investigation.model.NPC;

/**
 * Deterministic consistency checks for generated (and loaded) cases. Rejects cases the
 * game could not be played with, so bad LLM output never reaches the Game.
 */
public final class CaseValidator {
    public static final int MIN_LOCATIONS = 3;
    public static final int MIN_NPCS = 3;
    public static final int MIN_EVIDENCE = 4;

    private CaseValidator() { }

    /** Returns null if the case is valid, otherwise the first problem found. */
    public static String validate(Case c) {
        if (c.getTitle() == null || c.getTitle().isBlank()) {
            return "case has no title";
        }
        if (c.getLocations().size() < MIN_LOCATIONS) {
            return "needs at least " + MIN_LOCATIONS + " locations";
        }
        if (c.getNpcs().size() < MIN_NPCS) {
            return "needs at least " + MIN_NPCS + " people";
        }
        if (c.getAllEvidence().size() < MIN_EVIDENCE) {
            return "needs at least " + MIN_EVIDENCE + " pieces of evidence";
        }
        Set<String> ids = new HashSet<>();
        for (Location l : c.getLocations()) {
            if (!ids.add("L:" + l.getId())) {
                return "duplicate location id " + l.getId();
            }
            if (l.getName() == null || l.getName().isBlank()) {
                return "a location has no name";
            }
        }
        if (c.getStartLocation().isRestricted()) {
            return "the first location (crime scene) must not be restricted";
        }
        for (NPC n : c.getNpcs()) {
            if (!ids.add("N:" + n.getId())) {
                return "duplicate person id " + n.getId();
            }
            if (c.getLocation(n.getLocationId()) == null) {
                return n.getName() + " is placed at unknown location " + n.getLocationId();
            }
            if (n.getKnownFacts().isEmpty()) {
                return n.getName() + " knows no facts";
            }
        }
        for (Evidence e : c.getAllEvidence()) {
            if (!ids.add("E:" + e.getId())) {
                return "duplicate evidence id " + e.getId();
            }
            if (c.getLocation(e.getLocationId()) == null) {
                return "evidence " + e.getId() + " is at unknown location " + e.getLocationId();
            }
            if (e.getHiddenIn() == null || e.getHiddenIn().isBlank()) {
                return "evidence " + e.getId() + " has no hiding place";
            }
            if (e.getPointsToNpcId() != null && c.getNpc(e.getPointsToNpcId()) == null) {
                return "evidence " + e.getId() + " points to unknown person " + e.getPointsToNpcId();
            }
        }
        if (c.getCulpritId() == null || c.getNpc(c.getCulpritId()) == null) {
            return "the culprit must be one of the people";
        }
        long against = c.getAllEvidence().stream().filter(e -> c.getCulpritId().equals(e.getPointsToNpcId())).count();
        if (against < 2) {
            return "at least 2 pieces of evidence must point to the culprit";
        }
        if (c.getKeyEvidenceId() != null && c.getEvidence(c.getKeyEvidenceId()) == null) {
            return "keyEvidenceId does not match any evidence";
        }
        for (Location l : c.getLocations()) {
            if (!l.isRestricted()) {
                continue;
            }
            Evidence key = l.getUnlockEvidenceId() == null ? null : c.getEvidence(l.getUnlockEvidenceId());
            if (key == null) {
                return "restricted location " + l.getId() + " has no valid unlockEvidenceId";
            }
            Location keyLoc = c.getLocation(key.getLocationId());
            if (keyLoc.isRestricted()) {
                return "the clue that unlocks " + l.getId() + " must be in an unrestricted location";
            }
        }
        return null;
    }
}
