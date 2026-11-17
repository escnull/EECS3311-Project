package investigation.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import investigation.model.Case;
import investigation.model.EventType;
import investigation.model.Evidence;
import investigation.model.Game;
import investigation.model.Investigator;
import investigation.model.Lead;
import investigation.model.Location;
import investigation.model.NPC;
import investigation.state.ActionType;

/** Search or examine something at the current location to discover evidence (F06). */
public class SearchCommand extends AbstractCommand {
    private static final Set<String> GENERIC = Set.of(
            "room", "area", "everything", "around", "here", "place", "location", "all", "scene");
    private static final Set<String> STOP = Set.of(
            "the", "a", "an", "of", "in", "on", "under", "behind", "inside", "search", "examine", "check",
            "look", "at", "for", "and", "with", "near", "into", "to", "my", "his", "her", "their");

    private final String target;

    public SearchCommand(String target) {
        this.target = target == null ? "" : target.trim();
    }

    @Override
    protected String checkRules(Game game) {
        if (game.getCurrentLocation() == null) {
            return "You are not at any location.";
        }
        if (target.isEmpty()) {
            return "Search what? Name an object or area, e.g. 'search desk'.";
        }
        return null;
    }

    @Override
    public CommandOutcome execute(Game game) {
        Case c = game.getCase();
        Location here = game.getCurrentLocation();
        Investigator player = game.getPlayer();
        List<Evidence> candidates = c.getEvidenceAt(here.getId());
        boolean broad = isGeneric();

        List<Evidence> matches = new ArrayList<>();
        for (Evidence e : candidates) {
            boolean matchesTarget = matches(e);
            // A Methodical investigator can sweep a whole room with a general search.
            if (matchesTarget || (broad && player.has("Methodical"))) {
                matches.add(e);
            }
        }
        List<Evidence> newlyFound = new ArrayList<>();
        for (Evidence e : matches) {
            if (!e.isFound()) {
                newlyFound.add(e);
            }
        }
        if (newlyFound.isEmpty()) {
            if (!matches.isEmpty()) {
                return new CommandOutcome("You search the " + target + " again but find nothing new.");
            }
            String hint = broad && !player.has("Methodical")
                    ? " A general search turns up nothing; try naming a specific object." : "";
            return new CommandOutcome("You search the " + target + " but find nothing useful." + hint);
        }

        StringBuilder msg = new StringBuilder("You search the " + target + ".");
        List<String> changes = new ArrayList<>();
        boolean major = false;
        for (Evidence e : newlyFound) {
            e.markFound(game.getTurn());
            msg.append("\nEvidence found: ").append(e.getDetails());
            game.notifyObservers(EventType.EVIDENCE_FOUND, e.getName() + ": " + e.getDescription(), e.getId());
            if (e.isMajor()) {
                major = true;
                player.updateReputation(1);
                changes.add("Reputation +1 for finding key evidence (" + e.getName() + ")");
            }
            NPC suspect = e.getPointsToNpcId() == null ? null : c.getNpc(e.getPointsToNpcId());
            if (suspect != null) {
                Lead lead = new Lead(e.getName() + " connects " + suspect.getName() + " to the case",
                        List.of(e.getId()), suspect.getId(), "system");
                if (c.addLead(lead)) {
                    changes.add("New lead: " + lead.getDescription());
                }
            }
            for (Location l : c.getLocations()) {
                if (l.isRestricted() && e.getId().equals(l.getUnlockEvidenceId())) {
                    changes.add("You can now access " + l.getName());
                    major = true;
                }
            }
        }
        CommandOutcome result = new CommandOutcome(msg.toString());
        changes.forEach(result::change);
        if (major) {
            result.major("The investigator discovered: " + newlyFound.stream().map(Evidence::getName)
                    .reduce((a, b) -> a + ", " + b).orElse(""));
        }
        return result;
    }

    private boolean isGeneric() {
        for (String w : words(target)) {
            if (!GENERIC.contains(w)) {
                return false;
            }
        }
        return true;
    }

    private boolean matches(Evidence e) {
        List<String> targetWords = words(target);
        List<String> evidenceWords = new ArrayList<>(words(e.getHiddenIn()));
        evidenceWords.addAll(words(e.getName()));
        for (String t : targetWords) {
            if (GENERIC.contains(t)) {
                continue;
            }
            for (String w : evidenceWords) {
                if (w.equals(t) || (t.length() >= 4 && (w.startsWith(t) || t.startsWith(w) && w.length() >= 4))) {
                    return true;
                }
            }
        }
        return false;
    }

    static List<String> words(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        for (String w : s.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.length() >= 2 && !STOP.contains(w)) {
                out.add(w.endsWith("s") && w.length() > 3 ? w.substring(0, w.length() - 1) : w);
            }
        }
        return out;
    }

    @Override
    public ActionType getActionType() {
        return ActionType.SEARCH;
    }

    @Override
    public String describe() {
        return "search " + target;
    }

    public String getTarget() {
        return target;
    }
}
