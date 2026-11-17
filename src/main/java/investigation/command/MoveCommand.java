package investigation.command;

import investigation.model.Case;
import investigation.model.EventType;
import investigation.model.Game;
import investigation.model.Location;
import investigation.state.ActionType;

/** Move the investigator to another location (F03). */
public class MoveCommand extends AbstractCommand {
    private final String locationId;

    public MoveCommand(String locationId) {
        this.locationId = locationId;
    }

    @Override
    protected String checkRules(Game game) {
        Case c = game.getCase();
        Location target = c.getLocation(locationId);
        if (target == null) {
            return "There is no location called '" + locationId + "' in this case.";
        }
        if (game.getCurrentLocation() != null && game.getCurrentLocation().getId().equals(locationId)) {
            return "You are already at " + target.getName() + ".";
        }
        if (!target.canAccess(game.getPlayer(), c)) {
            return target.getAccessDeniedReason(c);
        }
        return null;
    }

    @Override
    public CommandOutcome execute(Game game) {
        Location target = game.getCase().getLocation(locationId);
        boolean firstVisit = !game.getCase().getVisitedLocationIds().contains(locationId);
        game.setCurrentLocation(locationId);
        game.notifyObservers(EventType.MOVED, "Moved to " + target.getName(), locationId);
        CommandOutcome out = new CommandOutcome("You go to " + target.getName() + ".").arrivedAt(target);
        if (target.isRestricted() && firstVisit) {
            out.major("The investigator entered the restricted area " + target.getName())
               .change("Gained access to restricted location " + target.getName());
        }
        return out;
    }

    @Override
    public ActionType getActionType() {
        return ActionType.MOVE;
    }

    @Override
    public String describe() {
        return "go to " + locationId;
    }

    public String getLocationId() {
        return locationId;
    }
}
