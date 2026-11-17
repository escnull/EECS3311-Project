package investigation.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import investigation.casegen.CaseValidator;
import investigation.observer.GameObserver;
import investigation.state.GameState;
import investigation.state.NoCaseState;
import investigation.util.Json;

/**
 * Receiver of all commands, context of the State pattern and subject of the Observer pattern.
 * Game holds the authoritative state; the AI never modifies it directly.
 */
public class Game {
    public static final int SAVE_VERSION = 1;

    private Investigator player;
    private Case currentCase;
    private GameState currentState = new NoCaseState();
    private String currentLocationId;
    private int turn;
    private final List<GameObserver> observers = new CopyOnWriteArrayList<>();

    public void initialize() {
        currentCase = null;
        currentLocationId = null;
        turn = 0;
        currentState = new NoCaseState();
    }

    // ---------------------------------------------------------------- observer

    public void attach(GameObserver o) {
        if (!observers.contains(o)) {
            observers.add(o);
        }
    }

    public void detach(GameObserver o) {
        observers.remove(o);
    }

    public void notifyObservers(GameEvent e) {
        GameEvent stamped = e.getTurn() == 0 ? e.withTurn(turn) : e;
        for (GameObserver o : observers) {
            o.update(stamped);
        }
    }

    public void notifyObservers(EventType type, String details) {
        notifyObservers(new GameEvent(type, details));
    }

    public void notifyObservers(EventType type, String details, String subjectId) {
        notifyObservers(new GameEvent(type, details, subjectId, turn));
    }

    // ---------------------------------------------------------------- state

    /**
     * Re-checks objectives and lets the current GameState pick the next state.
     * Returns the objectives whose status changed.
     */
    public List<Objective> updateState() {
        List<Objective> changed = new ArrayList<>();
        if (currentCase != null) {
            for (Objective o : currentCase.getObjectives()) {
                if (o.evaluate(currentCase)) {
                    changed.add(o);
                }
            }
        }
        GameState before = currentState;
        currentState = currentState.next(this);
        if (!before.getStateName().equals(currentState.getStateName())) {
            notifyObservers(EventType.STATE_CHANGED, before.getStateName() + " -> " + currentState.getStateName());
        }
        if (!changed.isEmpty()) {
            notifyObservers(EventType.OBJECTIVES_UPDATED, changed.stream()
                    .map(Objective::toString).collect(Collectors.joining("\n")));
        }
        return changed;
    }

    public void nextTurn() {
        turn++;
    }

    public void setInvestigator(Investigator i) {
        this.player = i;
        notifyObservers(EventType.PLAYER_CREATED, i.getInfo());
    }

    public void setCase(Case c) {
        this.currentCase = c;
        this.currentLocationId = c.getStartLocation().getId();
        c.markVisited(currentLocationId);
        this.turn = 1;
    }

    public void clearCase() {
        currentCase = null;
        currentLocationId = null;
    }

    public void setCurrentLocation(String locationId) {
        this.currentLocationId = locationId;
        if (currentCase != null) {
            currentCase.markVisited(locationId);
        }
    }

    public Location getCurrentLocation() {
        return currentCase == null || currentLocationId == null ? null : currentCase.getLocation(currentLocationId);
    }

    public Investigator getPlayer() { return player; }
    public Case getCase() { return currentCase; }
    public GameState getState() { return currentState; }
    public int getTurn() { return turn; }

    // ---------------------------------------------------------------- persistence (F12)

    /**
     * Writes the full game to a temp file and then moves it into place, so a failed
     * write never destroys the previous save.
     */
    public void save(String file, List<GameEvent> history) throws IOException {
        if (player == null || currentCase == null) {
            throw new IOException("Nothing to save: create an investigator and start a case first.");
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", SAVE_VERSION);
        root.put("turn", turn);
        root.put("currentLocationId", currentLocationId);
        root.put("investigator", player.toMap());
        root.put("case", currentCase.toMap());
        root.put("history", history.stream().map(GameEvent::toMap).collect(Collectors.toList()));
        Path target = Path.of(file).toAbsolutePath();
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, Json.write(root), StandardCharsets.UTF_8);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        notifyObservers(EventType.GAME_SAVED, "Game saved to " + file);
    }

    /**
     * Reads and validates a save file. The current game is only replaced if the whole
     * file is valid. Returns the stored history so the caller can restore memory.
     */
    @SuppressWarnings("unchecked")
    public List<GameEvent> load(String file) throws IOException {
        Path path = Path.of(file);
        if (!Files.exists(path)) {
            throw new IOException("Save file not found: " + file);
        }
        Investigator loadedPlayer;
        Case loadedCase;
        String loc;
        int loadedTurn;
        List<GameEvent> history = new ArrayList<>();
        try {
            Object parsed = Json.parse(Files.readString(path, StandardCharsets.UTF_8));
            if (!(parsed instanceof Map)) {
                throw new IllegalArgumentException("root is not an object");
            }
            Map<String, Object> root = (Map<String, Object>) parsed;
            if (Json.integer(root, "version", -1) != SAVE_VERSION) {
                throw new IllegalArgumentException("unsupported save version");
            }
            if (!(root.get("investigator") instanceof Map) || !(root.get("case") instanceof Map)) {
                throw new IllegalArgumentException("missing investigator or case");
            }
            loadedPlayer = Investigator.fromMap((Map<String, Object>) root.get("investigator"));
            loadedCase = Case.fromMap((Map<String, Object>) root.get("case"));
            String problem = CaseValidator.validate(loadedCase);
            if (problem != null) {
                throw new IllegalArgumentException(problem);
            }
            loc = Json.str(root, "currentLocationId");
            if (loc == null || loadedCase.getLocation(loc) == null) {
                throw new IllegalArgumentException("current location is invalid");
            }
            loadedTurn = Json.integer(root, "turn", 1);
            for (Map<String, Object> ev : Json.objects(root, "history")) {
                history.add(GameEvent.fromMap(ev));
            }
        } catch (RuntimeException ex) {
            throw new IOException("Save file is corrupted or invalid (" + ex.getMessage() + ")", ex);
        }
        // Everything is valid: now replace the current game.
        this.player = loadedPlayer;
        this.currentCase = loadedCase;
        this.currentLocationId = loc;
        this.turn = loadedTurn;
        this.currentState = new NoCaseState().next(this);
        notifyObservers(EventType.GAME_LOADED, "Loaded '" + loadedCase.getTitle() + "' from " + file);
        return history;
    }
}
