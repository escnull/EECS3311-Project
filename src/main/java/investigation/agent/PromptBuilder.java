package investigation.agent;

import java.util.stream.Collectors;

import investigation.model.Case;
import investigation.model.Evidence;
import investigation.model.Game;
import investigation.model.Location;
import investigation.model.NPC;

/**
 * Describes the deterministic game state to the LLM in a fixed line format.
 * Hidden information (unfound evidence, secrets, the culprit) is never included.
 */
public final class PromptBuilder {
    private PromptBuilder() { }

    public static String describeState(Game game) {
        Case c = game.getCase();
        Location here = game.getCurrentLocation();
        StringBuilder sb = new StringBuilder();
        sb.append("CASE: ").append(c.getTitle()).append(" (").append(c.getCrimeType()).append(")\n");
        sb.append("CURRENT LOCATION: [").append(here.getId()).append("] ").append(here.getName()).append('\n');
        for (Location l : c.getLocations()) {
            sb.append("LOCATION [").append(l.getId()).append("] ").append(l.getName())
              .append(l.canAccess(game.getPlayer(), c) ? "" : " (locked)").append('\n');
        }
        for (NPC n : c.getNpcs()) {
            sb.append("PERSON [").append(n.getId()).append("] ").append(n.getName()).append(" - ")
              .append(n.getRole()).append(", at [").append(n.getLocationId()).append("]")
              .append(n.getLocationId().equals(here.getId()) ? " (here)" : "").append('\n');
        }
        String objects = c.getEvidenceAt(here.getId()).stream().map(Evidence::getHiddenIn).distinct()
                .collect(Collectors.joining(", "));
        sb.append("SEARCHABLE HERE: ").append(objects.isEmpty() ? "(nothing notable)" : objects).append('\n');
        for (Evidence e : c.getFoundEvidence()) {
            sb.append("EVIDENCE COLLECTED [").append(e.getId()).append("] ").append(e.getName())
              .append(": ").append(e.getDescription()).append('\n');
        }
        return sb.toString();
    }
}
