package investigation.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import investigation.agent.AgentController;
import investigation.agent.AgentDecision;
import investigation.agent.LeadSuggestion;
import investigation.agent.MemoryManager;
import investigation.agent.strategy.EvasiveStrategy;
import investigation.agent.strategy.TruthfulStrategy;
import investigation.casegen.CaseGenerationException;
import investigation.command.AccuseCommand;
import investigation.command.CommandOutcome;
import investigation.command.GameCommand;
import investigation.command.MoveCommand;
import investigation.command.QuestionNpcCommand;
import investigation.command.SearchCommand;
import investigation.model.Case;
import investigation.model.CaseStatus;
import investigation.model.EventType;
import investigation.model.Evidence;
import investigation.model.Game;
import investigation.model.GameEvent;
import investigation.model.Investigator;
import investigation.model.Lead;
import investigation.model.Location;
import investigation.model.NPC;
import investigation.model.Objective;
import investigation.state.ActionType;

/**
 * Single entry point used by both GameGUI and GameCLI. It is the invoker of the Command
 * pattern: every command goes through executeCommand(), which validates before executing.
 * All output is published as GameEvents so every interface stays in sync.
 */
public class GameController {
    public static final String DEFAULT_SAVE = "saves/savegame.json";

    private final Game game;
    private final AgentController agentController;
    private final MemoryManager memoryManager;

    public GameController(Game game, AgentController agentController, MemoryManager memoryManager) {
        this.game = game;
        this.agentController = agentController;
        this.memoryManager = memoryManager;
        game.attach(memoryManager);
    }

    // ------------------------------------------------------------------ F01

    public boolean createInvestigator(String name, List<String> attributes) {
        String problem = Investigator.validate(name, attributes);
        if (problem != null) {
            error(problem);
            return false;
        }
        List<String> canonical = attributes.stream().map(Investigator::normalise).collect(Collectors.toList());
        game.setInvestigator(new Investigator(name.trim(), canonical));
        message("Welcome, Investigator " + name.trim() + ". Start a new investigation when you are ready.");
        return true;
    }

    // ------------------------------------------------------------------ F02

    public boolean startGame(String caseType, String preferences) {
        if (game.getPlayer() == null) {
            error("Create an investigator before starting an investigation.");
            return false;
        }
        if (!game.getState().handleAction(ActionType.NEW_CASE)) {
            error(game.getState().getRejectionReason(ActionType.NEW_CASE));
            return false;
        }
        message("The agent is building a new " + (caseType == null || caseType.isBlank() ? "random" : caseType)
                + " case. This can take a few seconds...");
        Case c;
        try {
            c = agentController.generateCase(caseType, preferences);
        } catch (CaseGenerationException e) {
            error(e.getMessage() + " No case was added.");
            return false;
        }
        game.initialize();
        game.setCase(c);
        game.notifyObservers(EventType.CASE_STARTED, c.getBriefing(), c.getStartLocation().getId());
        game.updateState();
        message("You arrive at the " + c.getStartLocation().getName() + ".");
        message(describeLocation(c.getStartLocation(), agentController.describeArrival(game, c.getStartLocation())));
        return true;
    }

    /** Closes the current case as failed so a new one can be started. */
    public void abandonCase() {
        Case c = game.getCase();
        if (c == null || c.getStatus() != CaseStatus.OPEN) {
            error("There is no open case to abandon.");
            return;
        }
        c.updateStatus(CaseStatus.FAILED);
        game.notifyObservers(EventType.CASE_FAILED, "You abandoned the case '" + c.getTitle() + "'.");
        game.updateState();
    }

    // ------------------------------------------------------------------ F04

    /** Free-text input: the agent plans commands, the game validates and executes them. */
    public void processInput(String input) {
        if (input == null || input.isBlank()) {
            return;
        }
        if (game.getPlayer() == null) {
            error("Create an investigator first.");
            return;
        }
        if (game.getCase() == null || game.getCase().getStatus() != CaseStatus.OPEN) {
            error(game.getState().getRejectionReason(ActionType.MOVE));
            return;
        }
        AgentDecision decision = agentController.interpretAction(input, game);
        if (decision.getError() != null) {
            error(decision.getError());
            return;
        }
        if (decision.getReasoning() != null && !decision.getCommands().isEmpty()) {
            message("Agent plan: " + decision.getCommands().stream().map(GameCommand::describe)
                    .collect(Collectors.joining(" -> ")));
        }
        for (GameCommand command : decision.getCommands()) {
            if (!executeCommand(command)) {
                if (decision.getCommands().size() > 1) {
                    message("Stopped the remaining steps because that one could not be done.");
                }
                return;
            }
        }
        if (decision.isLookRequested()) {
            look();
        }
        if (decision.getPendingConfirmation() instanceof AccuseCommand) {
            AccuseCommand a = (AccuseCommand) decision.getPendingConfirmation();
            NPC n = game.getCase().getNpc(a.getNpcId());
            message("Accusing " + n.getName() + " is final and affects the outcome of the case. "
                    + "To confirm, use the Accuse button or type: accuse " + n.getId());
        }
        if (decision.getClarification() != null && decision.getCommands().isEmpty()
                && !decision.isLookRequested() && decision.getPendingConfirmation() == null) {
            message("Agent: " + decision.getClarification());
        }
    }

    // ------------------------------------------------------------------ F08 invoker

    /**
     * Validates and executes one command, then runs the AI follow-ups the outcome asks for
     * (arrival narration, NPC reply, consequence). Returns false if the command was rejected.
     */
    public boolean executeCommand(GameCommand command) {
        if (!command.validate(game)) {
            game.notifyObservers(EventType.ACTION_REJECTED, "Cannot " + command.describe() + ": "
                    + command.getRejectionReason());
            return false;
        }
        game.nextTurn();
        CommandOutcome outcome = command.execute(game);
        if (outcome.getMessage() != null) {
            message(outcome.getMessage());
        }
        if (outcome.getArrivedAt() != null) {
            Location l = outcome.getArrivedAt();
            String narration = agentController.describeArrival(game, l);
            game.notifyObservers(EventType.NARRATION, narration, l.getId());
            message(describeLocation(l, null));
        }
        if (outcome.getNpcToRespond() != null) {
            respond(outcome.getNpcToRespond(), outcome.getQuestion(), outcome.getRelationshipDelta(), outcome);
        }
        if (!outcome.getDeterministicChanges().isEmpty()) {
            game.notifyObservers(EventType.RULE_CHANGES, String.join("\n", outcome.getDeterministicChanges()));
        }
        game.updateState();
        if (outcome.isMajorDecision()) {
            String narrative = agentController.generateConsequence(game, outcome);
            game.notifyObservers(EventType.CONSEQUENCE, narrative);
        }
        return true;
    }

    private void respond(NPC npc, String question, int delta, CommandOutcome outcome) {
        String reply = agentController.generateDialogue(game, npc, question);
        game.notifyObservers(EventType.DIALOGUE, npc.getName() + ": \"" + reply + "\"", npc.getId());
        int before = npc.getRelationship();
        npc.updateRelationship(delta);
        if (npc.getRelationship() != before) {
            message("(" + npc.getName() + "'s attitude toward you: " + before + " -> " + npc.getRelationship() + ")");
        }
        boolean isCulprit = npc.getId().equals(game.getCase().getCulpritId());
        // The strategy can change at runtime depending on the relationship.
        if (npc.getStrategy() instanceof EvasiveStrategy && !isCulprit && npc.getRelationship() >= 3) {
            npc.setStrategy(new TruthfulStrategy());
            outcome.major(npc.getName() + " decided to trust the investigator")
                   .change(npc.getName() + " is now willing to tell the whole truth");
        } else if (npc.getStrategy() instanceof TruthfulStrategy && npc.getRelationship() <= -3) {
            npc.setStrategy(new EvasiveStrategy());
            outcome.major(npc.getName() + " was offended by the investigator")
                   .change(npc.getName() + " has become guarded and evasive");
        }
        if (npc.getRelationship() <= NPC.MIN_RELATIONSHIP) {
            npc.setRefusesToTalk(true);
            outcome.major(npc.getName() + " refused to cooperate any further")
                   .change(npc.getName() + " will no longer talk to you");
        }
    }

    // ------------------------------------------------------------------ direct commands (CLI/GUI buttons)

    public boolean move(String locationIdOrName) {
        Case c = requireCase();
        if (c == null) {
            return false;
        }
        Location l = findLocation(locationIdOrName);
        return executeCommand(new MoveCommand(l == null ? locationIdOrName : l.getId()));
    }

    public boolean search(String target) {
        return requireCase() != null && executeCommand(new SearchCommand(target));
    }

    public boolean question(String npcIdOrName, String question) {
        if (requireCase() == null) {
            return false;
        }
        NPC n = findNpc(npcIdOrName);
        return executeCommand(new QuestionNpcCommand(n == null ? npcIdOrName : n.getId(), question));
    }

    public boolean accuse(String npcIdOrName) {
        if (requireCase() == null) {
            return false;
        }
        NPC n = findNpc(npcIdOrName);
        return executeCommand(new AccuseCommand(n == null ? npcIdOrName : n.getId()));
    }

    public void look() {
        Location here = game.getCurrentLocation();
        if (here == null) {
            error("You are not at any location yet.");
            return;
        }
        message(describeLocation(here, here.getDescription()));
    }

    // ------------------------------------------------------------------ F07 / F11

    public Case getCase() {
        return game.getCase();
    }

    public void suggestLeads() {
        Case c = requireCase();
        if (c == null) {
            return;
        }
        if (!game.getState().handleAction(ActionType.SUGGEST_LEADS)) {
            error(game.getState().getRejectionReason(ActionType.SUGGEST_LEADS));
            return;
        }
        message("The agent is reviewing your evidence...");
        LeadSuggestion s = agentController.suggestLeads(game);
        List<Lead> added = new ArrayList<>();
        for (Lead l : s.getLeads()) {
            if (c.addLead(l)) {
                added.add(l);
            }
        }
        List<Objective> newObjectives = agentController.objectivesForLeads(c, added);
        newObjectives.forEach(c::addObjective);
        if (!added.isEmpty()) {
            game.notifyObservers(EventType.LEADS_UPDATED, "Agent suggested leads:\n- " + added.stream()
                    .map(Lead::getDescription).collect(Collectors.joining("\n- ")));
        }
        if (!newObjectives.isEmpty()) {
            game.notifyObservers(EventType.OBJECTIVES_UPDATED, "New objectives:\n" + newObjectives.stream()
                    .map(Objective::toString).collect(Collectors.joining("\n")));
        }
        message(added.isEmpty() && !s.getLeads().isEmpty() ? "Those leads are already on your board." : s.getNote());
    }

    public void setLeadStatus(int index, Lead.Status status) {
        Case c = requireCase();
        if (c == null) {
            return;
        }
        if (index < 1 || index > c.getLeads().size()) {
            error("There is no lead number " + index + ".");
            return;
        }
        Lead l = c.getLeads().get(index - 1);
        l.setStatus(status);
        game.notifyObservers(EventType.LEADS_UPDATED, "Lead marked " + status + ": " + l.getDescription());
    }

    public List<Objective> getObjectives() {
        Case c = game.getCase();
        if (c == null) {
            return List.of();
        }
        game.updateState();
        return c.getObjectives();
    }

    // ------------------------------------------------------------------ F10

    public List<GameEvent> getHistory(String query) {
        return query == null || query.isBlank() ? memoryManager.getAll()
                : memoryManager.getRelevantHistory(query, 15);
    }

    // ------------------------------------------------------------------ F12

    public boolean saveGame(String file) {
        if (!game.getState().handleAction(ActionType.SAVE)) {
            error("Nothing to save yet: " + game.getState().getRejectionReason(ActionType.SAVE));
            return false;
        }
        try {
            game.save(file == null || file.isBlank() ? DEFAULT_SAVE : file, memoryManager.getAll());
            return true;
        } catch (IOException e) {
            error("Save failed: " + e.getMessage() + " The previous save was kept.");
            return false;
        }
    }

    public boolean loadGame(String file) {
        try {
            List<GameEvent> history = game.load(file == null || file.isBlank() ? DEFAULT_SAVE : file);
            memoryManager.restore(history);
            Location here = game.getCurrentLocation();
            message("Welcome back, " + game.getPlayer().getName() + ". " + game.getCase().getTitle()
                    + " - you are at the " + here.getName() + ".");
            return true;
        } catch (IOException e) {
            error("Load failed: " + e.getMessage() + " The current game is unchanged.");
            return false;
        }
    }

    // ------------------------------------------------------------------ helpers

    public Game getGame() { return game; }
    public AgentController getAgentController() { return agentController; }

    public String describeLocation(Location l, String narration) {
        Case c = game.getCase();
        StringBuilder sb = new StringBuilder();
        if (narration != null && !narration.isBlank()) {
            sb.append(narration).append('\n');
        }
        String people = c.getNpcsAt(l.getId()).stream().map(NPC::toString).collect(Collectors.joining(", "));
        sb.append("People here: ").append(people.isEmpty() ? "nobody" : people);
        List<Evidence> here = c.getEvidenceAt(l.getId());
        String objects = here.stream().map(Evidence::getHiddenIn).distinct().collect(Collectors.joining(", "));
        sb.append("\nThings to search: ").append(objects.isEmpty() ? "nothing obvious" : objects);
        if (game.getPlayer().has("Perceptive")) {
            here.stream().filter(e -> !e.isFound() && e.isMajor()).findFirst().ifPresent(e ->
                    sb.append("\n(Perceptive) Something about the ").append(e.getHiddenIn()).append(" catches your eye."));
        }
        String exits = c.getLocations().stream().filter(x -> !x.getId().equals(l.getId()))
                .map(x -> x.getName() + " [" + x.getId() + "]" + (x.canAccess(game.getPlayer(), c) ? "" : " (locked)"))
                .collect(Collectors.joining(", "));
        sb.append("\nOther locations: ").append(exits);
        return sb.toString();
    }

    private Case requireCase() {
        if (game.getPlayer() == null) {
            error("Create an investigator first.");
            return null;
        }
        if (game.getCase() == null) {
            error(game.getState().getRejectionReason(ActionType.MOVE));
            return null;
        }
        return game.getCase();
    }

    public Location findLocation(String text) {
        Case c = game.getCase();
        if (c == null || text == null) {
            return null;
        }
        String t = text.trim().toLowerCase(Locale.ROOT);
        for (Location l : c.getLocations()) {
            if (l.getId().equals(t) || l.getName().toLowerCase(Locale.ROOT).equals(t)
                    || l.getName().toLowerCase(Locale.ROOT).contains(t) && t.length() >= 3) {
                return l;
            }
        }
        return null;
    }

    private NPC findNpc(String text) {
        Case c = game.getCase();
        if (c == null || text == null) {
            return null;
        }
        String t = text.trim().toLowerCase(Locale.ROOT);
        for (NPC n : c.getNpcs()) {
            String name = n.getName().toLowerCase(Locale.ROOT);
            if (n.getId().equals(t) || name.equals(t) || (t.length() >= 3 && name.contains(t))
                    || n.getRole().equalsIgnoreCase(t)) {
                return n;
            }
        }
        return null;
    }

    private void message(String text) {
        game.notifyObservers(EventType.MESSAGE, text);
    }

    private void error(String text) {
        game.notifyObservers(EventType.ERROR, text);
    }
}
