package investigation.ui;

import investigation.model.GameEvent;

/** Turns GameEvents into display text, shared by the GUI and the CLI. Returns null to hide an event. */
public final class EventFormatter {
    private EventFormatter() { }

    public static String format(GameEvent e) {
        switch (e.getType()) {
            case MESSAGE: return e.getDetails();
            case ERROR: return "! " + e.getDetails();
            case ACTION_REJECTED: return "x " + e.getDetails();
            case PLAYER_CREATED: return "Investigator created.\n" + e.getDetails();
            case CASE_STARTED: return "\n==================== NEW CASE ====================\n" + e.getDetails()
                    + "\n==================================================";
            case NARRATION: return "~ " + e.getDetails();
            case DIALOGUE: return e.getDetails();
            case RULE_CHANGES: return "[Changes] " + e.getDetails().replace("\n", "\n          ");
            case CONSEQUENCE: return ">> " + e.getDetails();
            case LEADS_UPDATED: return "[Leads] " + e.getDetails();
            case OBJECTIVES_UPDATED: return "[Objectives]\n" + e.getDetails();
            case STATE_CHANGED: return "[Game state: " + e.getDetails() + "]";
            case CASE_SOLVED: return "*** " + e.getDetails() + " ***";
            case CASE_FAILED: return "*** CASE CLOSED: " + e.getDetails() + " ***";
            case GAME_SAVED:
            case GAME_LOADED: return e.getDetails();
            case MOVED:
            case EVIDENCE_FOUND:
            case ACCUSATION:
            default: return null;
        }
    }
}
