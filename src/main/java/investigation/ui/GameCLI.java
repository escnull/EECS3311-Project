package investigation.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import investigation.controller.GameController;
import investigation.model.Case;
import investigation.model.Evidence;
import investigation.model.GameEvent;
import investigation.model.Investigator;
import investigation.model.Lead;
import investigation.model.NPC;
import investigation.model.Objective;
import investigation.observer.GameObserver;

/** Command-line interface. Every command calls the same GameController the GUI uses. */
public class GameCLI implements GameObserver {
    private final GameController controller;
    private final PrintStream out;
    private boolean running = true;

    public GameCLI(GameController controller, PrintStream out) {
        this.controller = controller;
        this.out = out;
        controller.getGame().attach(this);
    }

    @Override
    public void update(GameEvent event) {
        String text = EventFormatter.format(event);
        if (text != null) {
            out.println(text);
        }
    }

    public void run() {
        out.println("=== AI Investigation Game (CLI) ===");
        out.println("LLM: " + controller.getAgentController().getLlmClient().getModelName());
        out.println("Type 'help' for commands. Anything that is not a command is sent to the AI agent.");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        while (running) {
            out.print(prompt());
            out.flush();
            String line;
            try {
                line = in.readLine();
            } catch (IOException e) {
                break;
            }
            if (line == null) {
                break;
            }
            if (!line.isBlank()) {
                out.println("> " + line.trim());
                handle(line.trim());
            }
        }
        out.println("Goodbye, detective.");
    }

    private String prompt() {
        Case c = controller.getCase();
        if (c == null || controller.getGame().getCurrentLocation() == null) {
            return "\n[" + controller.getGame().getState().getStateName() + "] > ";
        }
        return "\n[" + controller.getGame().getState().getStateName() + " | "
                + controller.getGame().getCurrentLocation().getName() + "] > ";
    }

    /** Executes one CLI line. Public so it can be scripted in tests. */
    public void handle(String line) {
        String[] parts = line.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1].trim() : "";
        switch (cmd) {
            case "help": help(); break;
            case "create": create(arg); break;
            case "new": newCase(arg); break;
            case "look": controller.look(); break;
            case "map": map(); break;
            case "go": case "move":
                if (controller.getCase() != null && controller.findLocation(arg) == null) {
                    controller.processInput(line); // e.g. "go to the kitchen and search the drawer"
                } else {
                    controller.move(arg);
                }
                break;
            case "search": case "examine":
                if (arg.matches(".*\\b(and|then)\\b.*")) {
                    controller.processInput(line);
                } else {
                    controller.search(arg);
                }
                break;
            case "ask": ask(arg); break;
            case "accuse": controller.accuse(arg); break;
            case "case": caseFile(); break;
            case "evidence": evidence(); break;
            case "suspects": case "people": suspects(); break;
            case "leads": leads(); break;
            case "suggest": controller.suggestLeads(); break;
            case "lead": lead(arg); break;
            case "objectives": objectives(); break;
            case "history": history(arg); break;
            case "save": controller.saveGame(arg); break;
            case "load": controller.loadGame(arg); break;
            case "abandon": controller.abandonCase(); break;
            case "profile": profile(); break;
            case "usage": out.println(controller.getAgentController().getLlmClient().getUsage()); break;
            case "quit": case "exit": running = false; break;
            default: controller.processInput(line);
        }
    }

    private void help() {
        out.println(String.join("\n",
                "Commands:",
                "  create <name> <attr>[,<attr>]   create investigator. Attributes: " + Investigator.ALLOWED_ATTRIBUTES,
                "  new [murder|theft|random] [preferences]   start an AI-generated case",
                "  look | map                     describe this location / list locations",
                "  go <location>                  move (e.g. go library)",
                "  search <object>                search something here (e.g. search desk)",
                "  ask <person> <question>        question someone here",
                "  accuse <person>                formally accuse a suspect (final)",
                "  case | evidence | suspects     view the case file",
                "  leads | suggest                view leads / ask the AI agent for new connections",
                "  lead <n> confirm|dismiss       update a lead",
                "  objectives                     view case progress",
                "  history [keywords]             view investigation memory",
                "  save [file] | load [file]      save or load (default " + GameController.DEFAULT_SAVE + ")",
                "  abandon | profile | usage | quit",
                "Anything else, e.g. \"go to the garden and search the rose bushes\", is interpreted by the AI agent."));
    }

    private void create(String arg) {
        String[] p = arg.split("\\s+");
        if (p.length < 2) {
            out.println("Usage: create <name> <attribute>[,<attribute>]  e.g. create Sam Perceptive,Charismatic");
            return;
        }
        String attrs = p[p.length - 1];
        String name = String.join(" ", Arrays.copyOf(p, p.length - 1));
        List<String> list = Arrays.stream(attrs.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        controller.createInvestigator(name, list);
    }

    private void newCase(String arg) {
        String[] p = arg.split("\\s+", 2);
        String type = p[0].isEmpty() ? "random" : p[0];
        String prefs = p.length > 1 ? p[1] : "";
        if (!type.equalsIgnoreCase("random") && !controller.getAgentController().getCaseTypes().contains(type.toLowerCase())) {
            prefs = arg;
            type = "random";
        }
        controller.startGame(type, prefs);
    }

    private void ask(String arg) {
        String[] p = arg.split("\\s+", 2);
        if (p.length < 2) {
            out.println("Usage: ask <person> <question>   e.g. ask edmund where were you last night?");
            return;
        }
        controller.question(p[0], p[1]);
    }

    private Case caseOrWarn() {
        Case c = controller.getCase();
        if (c == null) {
            out.println("No case yet. Use 'new' to start one.");
        }
        return c;
    }

    private void map() {
        Case c = caseOrWarn();
        if (c == null) {
            return;
        }
        String here = controller.getGame().getCurrentLocation().getId();
        c.getLocations().forEach(l -> out.println((l.getId().equals(here) ? " * " : "   ") + l.getName()
                + " [" + l.getId() + "]" + (l.canAccess(controller.getGame().getPlayer(), c) ? "" : " (locked)")
                + (c.getVisitedLocationIds().contains(l.getId()) ? " (visited)" : "")));
    }

    private void caseFile() {
        Case c = caseOrWarn();
        if (c == null) {
            return;
        }
        out.println(c.getBriefing());
        out.println("Status: " + c.getStatus() + " | Failed accusations: " + c.getWrongAccusations() + "/"
                + Case.MAX_WRONG_ACCUSATIONS);
        suspects();
        evidence();
        leads();
    }

    private void suspects() {
        Case c = caseOrWarn();
        if (c == null) {
            return;
        }
        out.println("People:");
        for (NPC n : c.getNpcs()) {
            out.println("  [" + n.getId() + "] " + n + " at " + c.getLocation(n.getLocationId()).getName()
                    + " | attitude " + n.getRelationship() + " | questioned " + n.getInterviewCount() + "x"
                    + (n.refusesToTalk() ? " | refuses to talk" : ""));
        }
    }

    private void evidence() {
        Case c = caseOrWarn();
        if (c == null) {
            return;
        }
        List<Evidence> found = c.getFoundEvidence();
        out.println("Evidence (" + found.size() + "):");
        found.forEach(e -> out.println("  - " + e.getDetails()));
    }

    private void leads() {
        Case c = caseOrWarn();
        if (c == null) {
            return;
        }
        out.println("Leads:");
        List<Lead> leads = c.getLeads();
        if (leads.isEmpty()) {
            out.println("  (none yet - try 'suggest')");
        }
        for (int i = 0; i < leads.size(); i++) {
            out.println("  " + (i + 1) + ". " + leads.get(i));
        }
    }

    private void lead(String arg) {
        String[] p = arg.split("\\s+");
        if (p.length != 2) {
            out.println("Usage: lead <number> confirm|dismiss|open");
            return;
        }
        try {
            Lead.Status s = p[1].startsWith("c") ? Lead.Status.CONFIRMED
                    : p[1].startsWith("d") ? Lead.Status.DISMISSED : Lead.Status.OPEN;
            controller.setLeadStatus(Integer.parseInt(p[0]), s);
        } catch (NumberFormatException e) {
            out.println("Lead number must be a number.");
        }
    }

    private void objectives() {
        if (caseOrWarn() == null) {
            return;
        }
        List<Objective> list = controller.getObjectives();
        long done = list.stream().filter(Objective::isCompleted).count();
        out.println("Objectives (" + done + "/" + list.size() + " complete):");
        list.forEach(o -> out.println("  " + o));
    }

    private void history(String query) {
        List<GameEvent> events = controller.getHistory(query);
        if (events.isEmpty()) {
            out.println("No events recorded yet.");
            return;
        }
        out.println(query.isBlank() ? "Investigation history:" : "History relevant to '" + query + "':");
        events.forEach(e -> out.println("  " + e));
    }

    private void profile() {
        if (controller.getGame().getPlayer() == null) {
            out.println("No investigator yet.");
        } else {
            out.println(controller.getGame().getPlayer().getInfo());
        }
    }
}
