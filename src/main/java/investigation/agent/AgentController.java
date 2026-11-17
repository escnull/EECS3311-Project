package investigation.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import investigation.agent.strategy.AgentStrategy;
import investigation.agent.strategy.DialogueContext;
import investigation.casegen.CaseCreator;
import investigation.casegen.CaseGenerationException;
import investigation.casegen.MurderCaseCreator;
import investigation.casegen.TheftCaseCreator;
import investigation.command.AccuseCommand;
import investigation.command.CommandOutcome;
import investigation.command.MoveCommand;
import investigation.command.QuestionNpcCommand;
import investigation.command.SearchCommand;
import investigation.llm.LLMClient;
import investigation.llm.LLMException;
import investigation.model.Case;
import investigation.model.CaseStatus;
import investigation.model.EventType;
import investigation.model.Evidence;
import investigation.model.Game;
import investigation.model.Lead;
import investigation.model.Location;
import investigation.model.NPC;
import investigation.model.Objective;
import investigation.util.Json;

/**
 * The AI agent. It reads deterministic state and memory, calls the LLM, and turns the
 * results into commands, dialogue, narrative and leads. It never changes Game directly:
 * everything it proposes is validated by the game system first.
 */
public class AgentController {
    public static final int MAX_PLAN_STEPS = 3;
    private static final int DIALOGUE_ATTEMPTS = 3;
    private static final int NARRATIVE_ATTEMPTS = 2;

    private final LLMClient llmClient;
    private final MemoryManager memoryManager;
    private final Map<String, CaseCreator> creators = new LinkedHashMap<>();
    private final Random random;

    public AgentController(LLMClient llmClient, MemoryManager memoryManager) {
        this(llmClient, memoryManager, new Random());
    }

    public AgentController(LLMClient llmClient, MemoryManager memoryManager, Random random) {
        this.llmClient = llmClient;
        this.memoryManager = memoryManager;
        this.random = random;
        register(new MurderCaseCreator());
        register(new TheftCaseCreator());
    }

    /** New case types plug in here without changing generateCase (Factory Method). */
    public void register(CaseCreator creator) {
        creators.put(creator.getCrimeType(), creator);
    }

    public Set<String> getCaseTypes() {
        return creators.keySet();
    }

    public LLMClient getLlmClient() {
        return llmClient;
    }

    // ------------------------------------------------------------------ F02 case generation

    public Case generateCase(String type, String preferences) throws CaseGenerationException {
        CaseCreator creator;
        if (type == null || type.isBlank() || type.equalsIgnoreCase("random")) {
            List<CaseCreator> all = new ArrayList<>(creators.values());
            creator = all.get(random.nextInt(all.size()));
        } else {
            creator = creators.get(type.toLowerCase(Locale.ROOT));
            if (creator == null) {
                throw new CaseGenerationException("Unknown case type '" + type + "'. Choose one of " + creators.keySet());
            }
        }
        return creator.createCase(llmClient, preferences == null ? "" : preferences.trim());
    }

    // ------------------------------------------------------------------ F04 natural language

    /**
     * Plans up to three game commands from free text. Uses relevant memory so references like
     * "go back to where I found the letter" can be resolved.
     */
    public AgentDecision interpretAction(String input, Game game) {
        String prompt = "TASK: INTERPRET_ACTION\n"
                + "Convert the player's request into game actions.\n\n"
                + PromptBuilder.describeState(game)
                + "\nRELEVANT MEMORY:\n" + memoryManager.asPromptContext(input, 6)
                + "\n\nPLAYER INPUT: " + input + "\n\n"
                + "Respond with JSON only, in this exact shape:\n"
                + "{\"reasoning\": \"one short sentence\",\n"
                + " \"steps\": [{\"action\": \"move|search|question|accuse|look\", \"target\": \"id or object\", "
                + "\"question\": \"only for question\"}],\n"
                + " \"clarification\": null}\n"
                + "Rules:\n"
                + "- move: target is a LOCATION id. question/accuse: target is a PERSON id.\n"
                + "- search: target is a short object or area name (1-3 words), e.g. one of SEARCHABLE HERE.\n"
                + "- look: describe the current location (no target).\n"
                + "- At most " + MAX_PLAN_STEPS + " steps, in the order the player wants them.\n"
                + "- Only use ids listed above. If the request is unclear, return no steps and put a short question "
                + "for the player in \"clarification\". If it is impossible or unrelated to the investigation, return "
                + "no steps and explain briefly in \"clarification\".";
        Map<String, Object> json;
        try {
            json = askJson(prompt);
        } catch (LLMException e) {
            return AgentDecision.error("The agent could not interpret that (" + e.getMessage() + "). Please try again.");
        }

        AgentDecision decision = new AgentDecision();
        decision.setReasoning(Json.str(json, "reasoning", null));
        List<Map<String, Object>> steps = Json.objects(json, "steps");
        String clarification = Json.str(json, "clarification", null);
        if (steps.isEmpty()) {
            decision.setClarification(clarification != null ? clarification
                    : "I'm not sure what you want to do. Try something like 'search the desk' or 'talk to the butler'.");
            return decision;
        }
        Case c = game.getCase();
        for (Map<String, Object> step : steps.subList(0, Math.min(MAX_PLAN_STEPS, steps.size()))) {
            String action = Json.str(step, "action", "").toLowerCase(Locale.ROOT).trim();
            String target = Json.str(step, "target", "");
            switch (action) {
                case "move": {
                    Location l = resolveLocation(c, target);
                    if (l == null) {
                        return AgentDecision.clarify("Which location do you mean by '" + target + "'?");
                    }
                    decision.addCommand(new MoveCommand(l.getId()));
                    break;
                }
                case "search":
                    decision.addCommand(new SearchCommand(target));
                    break;
                case "question": {
                    NPC n = resolveNpc(c, target);
                    if (n == null) {
                        return AgentDecision.clarify("Who do you want to talk to? I don't know '" + target + "'.");
                    }
                    String q = Json.str(step, "question", input);
                    decision.addCommand(new QuestionNpcCommand(n.getId(), q));
                    break;
                }
                case "accuse": {
                    NPC n = resolveNpc(c, target);
                    if (n == null) {
                        return AgentDecision.clarify("Who do you want to accuse?");
                    }
                    // An accusation is irreversible, so the agent never performs it on its own.
                    decision.setPendingConfirmation(new AccuseCommand(n.getId()));
                    break;
                }
                case "look":
                    decision.setLookRequested(true);
                    break;
                default:
                    return AgentDecision.clarify(clarification != null ? clarification
                            : "I couldn't turn that into an action. Try moving, searching, or questioning someone.");
            }
        }
        return decision;
    }

    static Location resolveLocation(Case c, String target) {
        if (target == null) {
            return null;
        }
        String t = target.toLowerCase(Locale.ROOT).trim();
        for (Location l : c.getLocations()) {
            if (l.getId().equalsIgnoreCase(t) || l.getName().equalsIgnoreCase(t)) {
                return l;
            }
        }
        for (Location l : c.getLocations()) {
            String name = l.getName().toLowerCase(Locale.ROOT);
            if (t.length() >= 3 && (name.contains(t) || t.contains(name) || l.getId().contains(t.replace(' ', '_')))) {
                return l;
            }
        }
        return null;
    }

    static NPC resolveNpc(Case c, String target) {
        if (target == null) {
            return null;
        }
        String t = target.toLowerCase(Locale.ROOT).trim();
        for (NPC n : c.getNpcs()) {
            if (n.getId().equalsIgnoreCase(t) || n.getName().equalsIgnoreCase(t)) {
                return n;
            }
        }
        for (NPC n : c.getNpcs()) {
            String name = n.getName().toLowerCase(Locale.ROOT);
            String role = n.getRole().toLowerCase(Locale.ROOT);
            for (String part : name.split("\\s+")) {
                if (part.length() >= 3 && (t.equals(part) || t.contains(part))) {
                    return n;
                }
            }
            if (t.length() >= 4 && (role.equals(t) || role.contains(t) || t.contains(role))) {
                return n;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ F03 arrival narration

    public String describeArrival(Game game, Location location) {
        Case c = game.getCase();
        String objects = c.getEvidenceAt(location.getId()).stream().map(Evidence::getHiddenIn).distinct()
                .collect(Collectors.joining(", "));
        String people = c.getNpcsAt(location.getId()).stream().map(NPC::toString).collect(Collectors.joining(", "));
        String prompt = "TASK: DESCRIBE_LOCATION\n"
                + "LOCATION: " + location.getName() + " - " + location.getDescription() + "\n"
                + "OBJECTS: " + (objects.isEmpty() ? "none" : objects) + "\n"
                + "PEOPLE: " + (people.isEmpty() ? "nobody" : people) + "\n"
                + "CASE: " + c.getBriefing() + "\n"
                + "RELEVANT MEMORY:\n" + memoryManager.asPromptContext(location.getName(), 4) + "\n\n"
                + "Write 2 short sentences, in second person, describing what the investigator notices on arriving. "
                + "Mention the listed objects naturally but do not say what is hidden in them. "
                + "Do not invent new people or clues. Plain text only.";
        List<String> hidden = c.getEvidenceAt(location.getId()).stream().filter(e -> !e.isFound())
                .map(Evidence::getName).collect(Collectors.toList());
        try {
            String text = clean(llmClient.generate(prompt), 500);
            for (String h : hidden) {
                if (text.toLowerCase(Locale.ROOT).contains(h.toLowerCase(Locale.ROOT))) {
                    return location.getDescription();
                }
            }
            return text;
        } catch (LLMException e) {
            return location.getDescription();
        }
    }

    // ------------------------------------------------------------------ F05 dialogue

    /**
     * Writes an NPC reply. The NPC's strategy decides what may be revealed; replies that leak
     * forbidden facts are rejected and regenerated, then a safe fallback line is used.
     */
    public String generateDialogue(Game game, NPC npc, String question) {
        Case c = game.getCase();
        AgentStrategy strategy = npc.getStrategy();
        DialogueContext ctx = new DialogueContext(npc, game.getPlayer(), question,
                npc.getId().equals(c.getCulpritId()), c.countFoundEvidenceAgainst(npc.getId()));
        String guidance = strategy.execute(ctx);
        List<String> forbidden = strategy.forbiddenFacts(ctx);
        String basePrompt = "TASK: NPC_DIALOGUE\n"
                + "You are " + npc.getName() + ", the " + npc.getRole() + ". Personality: " + npc.getPersonality() + ".\n"
                + "Relationship with the investigator (-5 hostile .. +5 friendly): " + npc.getRelationship() + "\n"
                + guidance + "\n"
                + "CASE CONTEXT: " + c.getBriefing() + "\n"
                + "PAST EVENTS INVOLVING YOU:\n"
                + memoryManager.asPromptContext(npc.getName() + " " + npc.getId() + " " + question, 5) + "\n\n"
                + "INVESTIGATOR " + game.getPlayer().getName() + " ASKS: \"" + question + "\"\n\n"
                + "Reply in character in at most 3 sentences. Only use the allowed facts; do not invent new evidence, "
                + "people or places. Never confess. Output only the spoken reply.";
        String prompt = basePrompt;
        for (int attempt = 1; attempt <= DIALOGUE_ATTEMPTS; attempt++) {
            try {
                String reply = clean(llmClient.generate(prompt), 700);
                if (!reply.isEmpty() && !FactChecker.leaks(reply, forbidden)) {
                    return reply;
                }
                prompt = basePrompt + "\n\nYour previous reply revealed something this character must keep hidden. "
                        + "Answer again without it.";
            } catch (LLMException e) {
                game.notifyObservers(EventType.ERROR, "LLM unavailable for dialogue: " + e.getMessage());
                break;
            }
        }
        return strategy.fallbackLine(ctx);
    }

    // ------------------------------------------------------------------ F09 consequences

    public String generateConsequence(Game game, CommandOutcome outcome) {
        Case c = game.getCase();
        String changes = outcome.getDeterministicChanges().isEmpty() ? "(no rule changes)"
                : "- " + String.join("\n- ", outcome.getDeterministicChanges());
        String fallback = "Consequence: " + String.join("; ", outcome.getDeterministicChanges());
        NPC culprit = c.getNpc(c.getCulpritId());
        String prompt = "TASK: CONSEQUENCE\n"
                + "DECISION: " + outcome.getDecisionSummary() + "\n"
                + "RULE CHANGES ALREADY APPLIED:\n" + changes + "\n"
                + "CASE: " + c.getBriefing() + " Status: " + c.getStatus() + "\n"
                + "PEOPLE: " + c.getNpcs().stream().map(NPC::toString).collect(Collectors.joining(", ")) + "\n"
                + "EVIDENCE FOUND: " + c.getFoundEvidence().stream().map(Evidence::getName)
                        .collect(Collectors.joining(", ")) + "\n"
                + "RELEVANT MEMORY:\n" + memoryManager.asPromptContext(outcome.getDecisionSummary(), 4) + "\n\n"
                + "Write 2-3 sentences in second person narrating how the world reacts to this decision. "
                + "Stay consistent with the rule changes. Do not invent new evidence"
                + (c.getStatus() == CaseStatus.OPEN ? " and do not reveal who the culprit is." : ".")
                + " Plain text only.";
        for (int attempt = 1; attempt <= NARRATIVE_ATTEMPTS; attempt++) {
            try {
                String text = clean(llmClient.generate(prompt), 900);
                boolean spoils = c.getStatus() == CaseStatus.OPEN && culprit != null
                        && FactChecker.revealsCulprit(text, culprit.getName());
                if (!text.isEmpty() && !spoils) {
                    return text;
                }
            } catch (LLMException e) {
                return fallback;
            }
        }
        return fallback;
    }

    // ------------------------------------------------------------------ F07 leads

    public LeadSuggestion suggestLeads(Game game) {
        Case c = game.getCase();
        List<Evidence> found = c.getFoundEvidence();
        if (found.isEmpty()) {
            return new LeadSuggestion(List.of(), "Collect some evidence first; the agent only connects known facts.");
        }
        StringBuilder sb = new StringBuilder("TASK: SUGGEST_LEADS\n");
        sb.append("CASE: ").append(c.getBriefing()).append('\n');
        for (Evidence e : found) {
            sb.append("EVIDENCE [").append(e.getId()).append("] ").append(e.getName()).append(": ")
              .append(e.getDescription()).append('\n');
        }
        for (NPC n : c.getNpcs()) {
            sb.append("PERSON [").append(n.getId()).append("] ").append(n.getName()).append(" - ").append(n.getRole())
              .append(", ").append(n.getPersonality()).append('\n');
        }
        for (Lead l : c.getLeads()) {
            sb.append("EXISTING LEAD: ").append(l.getDescription()).append('\n');
        }
        sb.append("RELEVANT MEMORY:\n").append(memoryManager.asPromptContext("evidence lead suspect", 6)).append("\n\n");
        sb.append("Suggest up to 3 NEW, specific leads that connect the listed evidence to people. Respond with JSON only:\n"
                + "{\"leads\": [{\"description\": \"one sentence\", \"evidenceIds\": [\"id\"], \"npcId\": \"id or null\"}]}\n"
                + "Use only the evidence and person ids listed above.");
        Map<String, Object> json;
        try {
            json = askJson(sb.toString());
        } catch (LLMException e) {
            return new LeadSuggestion(List.of(), "The agent could not suggest leads right now (" + e.getMessage()
                    + "). Showing existing leads only.");
        }
        Set<String> foundIds = found.stream().map(Evidence::getId).collect(Collectors.toSet());
        List<Lead> valid = new ArrayList<>();
        int rejected = 0;
        for (Map<String, Object> m : Json.objects(json, "leads")) {
            String desc = Json.str(m, "description", "").trim();
            List<String> ids = Json.strings(m, "evidenceIds");
            String npcId = Json.str(m, "npcId", null);
            boolean ok = !desc.isEmpty() && desc.length() <= 250 && !ids.isEmpty() && foundIds.containsAll(ids)
                    && (npcId == null || c.getNpc(npcId) != null);
            if (ok && valid.size() < 3) {
                valid.add(new Lead(desc, ids, npcId, "agent"));
            } else {
                rejected++;
            }
        }
        String note = valid.isEmpty() ? "The agent found no new connections based on known evidence."
                : "The agent suggested " + valid.size() + " lead(s).";
        if (rejected > 0) {
            note += " (" + rejected + " suggestion(s) rejected for referring to unknown evidence or people.)";
        }
        return new LeadSuggestion(valid, note);
    }

    /** F11: the agent turns new person-related leads into follow-up objectives. */
    public List<Objective> objectivesForLeads(Case c, List<Lead> leads) {
        List<Objective> out = new ArrayList<>();
        for (Lead l : leads) {
            NPC n = l.getNpcId() == null ? null : c.getNpc(l.getNpcId());
            if (n == null || n.refusesToTalk()) {
                continue;
            }
            boolean exists = c.getObjectives().stream().anyMatch(o -> o.getType() == Objective.Type.QUESTION_NPC
                    && n.getId().equals(o.getTargetId()) && o.getCount() > n.getInterviewCount()
                    && o.getStatus() == Objective.Status.ACTIVE)
                    || out.stream().anyMatch(o -> n.getId().equals(o.getTargetId()));
            if (!exists) {
                out.add(new Objective("Follow up with " + n.getName() + " about: " + l.getDescription(),
                        Objective.Type.QUESTION_NPC, n.getId(), n.getInterviewCount() + 1, "agent"));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> askJson(String prompt) throws LLMException {
        String reply = llmClient.generate(prompt);
        try {
            return Json.extractObject(reply);
        } catch (IllegalArgumentException first) {
            String retry = llmClient.generate(prompt + "\n\nYour previous reply was not valid JSON. "
                    + "Reply with the JSON object only.");
            try {
                return Json.extractObject(retry);
            } catch (IllegalArgumentException second) {
                throw new LLMException("the model did not return valid JSON");
            }
        }
    }

    static String clean(String text, int maxLen) {
        String t = text == null ? "" : text.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length() - 1).trim();
        }
        if (t.length() > maxLen) {
            int cut = t.lastIndexOf('.', maxLen);
            t = cut > maxLen / 2 ? t.substring(0, cut + 1) : t.substring(0, maxLen) + "...";
        }
        return t;
    }
}
