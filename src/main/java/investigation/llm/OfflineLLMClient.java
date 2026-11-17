package investigation.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import investigation.util.Json;

/**
 * Test double for LLMClient, selected with LLM_PROVIDER=offline or --offline.
 * It lets the game, the unit tests and graders run without network access or an API key.
 * It answers each agent task with simple rule-based text; the real gameplay is meant to be
 * run with AIClient (Claude) or OpenAICompatibleClient.
 */
public class OfflineLLMClient implements LLMClient {
    private static final Pattern LOCATION = Pattern.compile("^LOCATION \\[([a-z0-9_]+)\\] (.+?)( \\(locked\\))?$");
    private static final Pattern PERSON = Pattern.compile("^PERSON \\[([a-z0-9_]+)\\] (.+?) - ([^,]+),");
    private static final Pattern EVIDENCE = Pattern.compile("^EVIDENCE(?: COLLECTED)? \\[([a-z0-9_]+)\\] ([^:]+): (.*)$");

    private final TokenUsage usage = new TokenUsage();

    @Override
    public String generate(String system, String prompt) {
        String reply;
        if (prompt.contains("TASK: GENERATE_CASE")) {
            reply = prompt.contains("CRIME TYPE: theft") ? theftCase() : murderCase();
        } else if (prompt.contains("TASK: INTERPRET_ACTION")) {
            reply = interpret(prompt);
        } else if (prompt.contains("TASK: NPC_DIALOGUE")) {
            reply = dialogue(prompt);
        } else if (prompt.contains("TASK: SUGGEST_LEADS")) {
            reply = leads(prompt);
        } else if (prompt.contains("TASK: DESCRIBE_LOCATION")) {
            reply = describe(prompt);
        } else if (prompt.contains("TASK: CONSEQUENCE")) {
            reply = consequence(prompt);
        } else {
            reply = "OK";
        }
        usage.record(prompt.length() / 4, reply.length() / 4);
        return reply;
    }

    @Override
    public String getModelName() {
        return "offline rule-based test client (no LLM)";
    }

    @Override
    public TokenUsage getUsage() {
        return usage;
    }

    // ----------------------------------------------------------------- interpret

    private String interpret(String prompt) {
        String input = line(prompt, "PLAYER INPUT: ").toLowerCase(Locale.ROOT);
        Map<String, String> locations = new LinkedHashMap<>();
        Map<String, String> people = new LinkedHashMap<>();
        for (String l : prompt.split("\n")) {
            Matcher m = LOCATION.matcher(l);
            if (m.find()) {
                locations.put(m.group(1), m.group(2).toLowerCase(Locale.ROOT));
            }
            Matcher p = PERSON.matcher(l);
            if (p.find()) {
                people.put(p.group(1), (p.group(2) + " " + p.group(3)).toLowerCase(Locale.ROOT));
            }
        }
        List<Map<String, Object>> steps = new ArrayList<>();
        for (String seg : input.split("\\s*(?:,|;| and then | then | and )\\s*")) {
            seg = seg.trim();
            if (seg.isEmpty()) {
                continue;
            }
            Map<String, Object> step = new LinkedHashMap<>();
            if (seg.matches("^(go|move|walk|head|visit|travel|enter|return)\\b.*")) {
                String id = match(seg, locations);
                if (id == null) {
                    return clarify("Where do you want to go?");
                }
                step.put("action", "move");
                step.put("target", id);
            } else if (seg.matches("^(accuse|arrest)\\b.*")) {
                String id = match(seg, people);
                if (id == null) {
                    return clarify("Who do you want to accuse?");
                }
                step.put("action", "accuse");
                step.put("target", id);
            } else if (seg.matches("^(ask|question|talk|interrogate|speak|interview)\\b.*")) {
                String id = match(seg, people);
                if (id == null) {
                    return clarify("Who do you want to talk to?");
                }
                step.put("action", "question");
                step.put("target", id);
                int about = seg.indexOf(" about ");
                step.put("question", about >= 0 ? "What can you tell me about " + seg.substring(about + 7) + "?"
                        : "What do you know about what happened?");
            } else if (seg.matches("^(look around|look|describe)$")) {
                step.put("action", "look");
            } else if (seg.matches("^(search|examine|inspect|check|investigate|open|look at|look in|look under)\\b.*")) {
                String target = seg.replaceFirst("^(search|examine|inspect|check|investigate|open|look at|look in|look under)\\s*", "")
                        .replaceFirst("^(the|a|an)\\s+", "").trim();
                step.put("action", "search");
                step.put("target", target.isEmpty() ? "room" : target);
            } else {
                return clarify("I'm not sure what you mean by '" + seg + "'. Try going somewhere, searching something, "
                        + "or talking to someone.");
            }
            steps.add(step);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reasoning", "Rule-based offline interpretation.");
        out.put("steps", steps);
        out.put("clarification", null);
        return Json.write(out);
    }

    /** Returns the candidate whose id or name word appears earliest in the segment. */
    private static String match(String seg, Map<String, String> candidates) {
        String best = null;
        int bestPos = Integer.MAX_VALUE;
        for (Map.Entry<String, String> e : candidates.entrySet()) {
            List<String> keys = new ArrayList<>();
            keys.add(e.getKey().replace('_', ' '));
            for (String word : e.getValue().split("[^a-z]+")) {
                if (word.length() >= 4) {
                    keys.add(word);
                }
            }
            for (String k : keys) {
                Matcher m = Pattern.compile("\\b" + Pattern.quote(k) + "\\b").matcher(seg);
                if (m.find() && m.start() < bestPos) {
                    bestPos = m.start();
                    best = e.getKey();
                }
            }
        }
        return best;
    }

    private static String clarify(String q) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("steps", List.of());
        out.put("clarification", q);
        return Json.write(out);
    }

    // ----------------------------------------------------------------- other tasks

    private String dialogue(String prompt) {
        List<String> facts = new ArrayList<>();
        boolean in = false;
        for (String l : prompt.split("\n")) {
            if (l.startsWith("ALLOWED FACTS:")) {
                in = true;
            } else if (in && l.startsWith("- ")) {
                facts.add(l.substring(2));
            } else if (in) {
                break;
            }
        }
        boolean evasive = prompt.contains("BEHAVIOUR: evasive");
        String q = line(prompt, "ASKS: ");
        if (facts.isEmpty()) {
            return evasive ? "I'd rather not discuss that." : "I wish I could help, but I don't know anything more.";
        }
        String fact = facts.get(Math.abs(q.hashCode()) % facts.size());
        return evasive ? "Why are you asking me? ... Fine. " + fact : "Of course, detective. " + fact;
    }

    private String leads(String prompt) {
        List<String[]> evidence = new ArrayList<>();
        Map<String, String> people = new LinkedHashMap<>();
        for (String l : prompt.split("\n")) {
            Matcher m = EVIDENCE.matcher(l);
            if (m.find()) {
                evidence.add(new String[] {m.group(1), m.group(2), m.group(3).toLowerCase(Locale.ROOT)});
            }
            Matcher p = Pattern.compile("^PERSON \\[([a-z0-9_]+)\\] ([^-]+) - ").matcher(l);
            if (p.find()) {
                people.put(p.group(1), p.group(2).trim());
            }
        }
        List<Map<String, Object>> leads = new ArrayList<>();
        for (String[] e : evidence) {
            for (Map.Entry<String, String> p : people.entrySet()) {
                String first = p.getValue().split(" ")[0].toLowerCase(Locale.ROOT);
                String[] parts = p.getValue().toLowerCase(Locale.ROOT).split(" ");
                String last = parts[parts.length - 1];
                if (e[2].contains(first) || e[2].contains(last) || e[2].contains(initials(p.getValue()))) {
                    Map<String, Object> lead = new LinkedHashMap<>();
                    lead.put("description", "The " + e[1].toLowerCase(Locale.ROOT) + " may connect "
                            + p.getValue() + " to the crime.");
                    lead.put("evidenceIds", List.of(e[0]));
                    lead.put("npcId", p.getKey());
                    leads.add(lead);
                }
            }
        }
        return Json.write(Map.of("leads", leads.subList(0, Math.min(3, leads.size()))));
    }

    private static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String p : name.split(" ")) {
            if (!p.isEmpty()) {
                sb.append(Character.toLowerCase(p.charAt(0))).append('.');
            }
        }
        return sb.toString();
    }

    private String describe(String prompt) {
        String loc = line(prompt, "LOCATION: ");
        String objects = line(prompt, "OBJECTS: ");
        String name = loc.contains(" - ") ? loc.substring(0, loc.indexOf(" - ")) : loc;
        return "You step into the " + name + ". You take note of the " + objects + ".";
    }

    private String consequence(String prompt) {
        String decision = line(prompt, "DECISION: ");
        return "Word travels fast: " + decision.toLowerCase(Locale.ROOT) + ". Everyone involved is watching your next move.";
    }

    private static String line(String prompt, String prefix) {
        for (String l : prompt.split("\n")) {
            int i = l.indexOf(prefix);
            if (i >= 0) {
                return l.substring(i + prefix.length()).replaceAll("^\"|\"$", "").trim();
            }
        }
        return "";
    }

    // ----------------------------------------------------------------- canned cases

    private static String murderCase() {
        return "{\"title\":\"Death at Ravenshollow Manor\",\"description\":\"Lord Albert Whitmore was found dead "
            + "at his study desk the morning after announcing changes to his will. The family insists it was his "
            + "heart, but the cold tea beside him smells bitter.\",\"subject\":\"Lord Albert Whitmore\","
            + "\"culpritId\":\"clara\",\"keyEvidenceId\":\"teacup\",\"locations\":["
            + "{\"id\":\"study\",\"name\":\"Study\",\"description\":\"Dark wood panels, a cold fireplace and the desk "
            + "where Lord Whitmore died.\",\"restricted\":false},"
            + "{\"id\":\"kitchen\",\"name\":\"Kitchen\",\"description\":\"Copper pots and the smell of bread; the "
            + "cook's domain.\",\"restricted\":false},"
            + "{\"id\":\"garden\",\"name\":\"Rose Garden\",\"description\":\"Overgrown rose bushes around a stone "
            + "fountain.\",\"restricted\":false},"
            + "{\"id\":\"library\",\"name\":\"Library\",\"description\":\"Floor-to-ceiling shelves and a reading "
            + "chair by the window.\",\"restricted\":false},"
            + "{\"id\":\"cellar\",\"name\":\"Wine Cellar\",\"description\":\"A locked, damp cellar lined with old "
            + "crates.\",\"restricted\":true,\"unlockEvidenceId\":\"cellar_key\",\"requiredReputation\":3}],"
            + "\"npcs\":["
            + "{\"id\":\"edmund\",\"name\":\"Edmund Price\",\"role\":\"butler\",\"personality\":\"formal and loyal\","
            + "\"locationId\":\"study\",\"behaviour\":\"truthful\",\"knownFacts\":[\"I brought his Lordship tea at "
            + "nine o'clock and he was alive and well.\",\"Miss Clara visited the study shortly after nine.\","
            + "\"The cellar key is kept in the kitchen drawer.\"],\"secret\":\"He has been quietly selling old wine "
            + "from the cellar.\"},"
            + "{\"id\":\"clara\",\"name\":\"Clara Whitmore\",\"role\":\"niece\",\"personality\":\"charming but sharp\","
            + "\"locationId\":\"garden\",\"behaviour\":\"evasive\",\"knownFacts\":[\"My uncle and I argued about the "
            + "will last week.\",\"I was in the garden all evening tending the roses.\"],\"secret\":\"She bought "
            + "arsenic and poured it into her uncle's tea to stop him changing the will.\"},"
            + "{\"id\":\"felix\",\"name\":\"Felix Hale\",\"role\":\"family doctor\",\"personality\":\"nervous and "
            + "precise\",\"locationId\":\"library\",\"behaviour\":\"evasive\",\"knownFacts\":[\"Lord Whitmore's "
            + "heart was strong for his age.\",\"The symptoms I saw are not typical of a heart attack.\"],"
            + "\"secret\":\"He owes Lord Whitmore a large sum of money from gambling.\"},"
            + "{\"id\":\"martha\",\"name\":\"Martha Cole\",\"role\":\"cook\",\"personality\":\"warm and talkative\","
            + "\"locationId\":\"kitchen\",\"behaviour\":\"truthful\",\"knownFacts\":[\"Miss Clara asked me which cup "
            + "was his Lordship's.\",\"I saw a lavender glove on the garden path this morning.\"],\"secret\":\"She "
            + "is planning to retire.\"}],"
            + "\"evidence\":["
            + "{\"id\":\"teacup\",\"name\":\"Bitter teacup\",\"description\":\"A teacup with a gritty white residue "
            + "that smells of almonds and metal. Someone added something to this tea.\",\"locationId\":\"study\","
            + "\"hiddenIn\":\"desk\",\"pointsToNpcId\":\"clara\",\"major\":true},"
            + "{\"id\":\"will_draft\",\"name\":\"Draft of the new will\",\"description\":\"A half-burned draft that "
            + "removes Clara Whitmore as heir.\",\"locationId\":\"study\",\"hiddenIn\":\"fireplace\","
            + "\"pointsToNpcId\":\"clara\",\"major\":true},"
            + "{\"id\":\"cellar_key\",\"name\":\"Cellar key\",\"description\":\"A heavy iron key labelled CELLAR.\","
            + "\"locationId\":\"kitchen\",\"hiddenIn\":\"drawer\",\"pointsToNpcId\":null,\"major\":false},"
            + "{\"id\":\"torn_glove\",\"name\":\"Torn lavender glove\",\"description\":\"A silk glove embroidered "
            + "C.W., stained with white powder.\",\"locationId\":\"garden\",\"hiddenIn\":\"rose bushes\","
            + "\"pointsToNpcId\":\"clara\",\"major\":true},"
            + "{\"id\":\"debt_letter\",\"name\":\"Letter about a debt\",\"description\":\"A letter from Lord "
            + "Whitmore demanding that Felix Hale repay his loan.\",\"locationId\":\"library\",\"hiddenIn\":"
            + "\"bookshelf\",\"pointsToNpcId\":\"felix\",\"major\":false},"
            + "{\"id\":\"poison_receipt\",\"name\":\"Chemist's receipt\",\"description\":\"A receipt for arsenic "
            + "'for garden pests', signed C.W.\",\"locationId\":\"cellar\",\"hiddenIn\":\"crate\","
            + "\"pointsToNpcId\":\"clara\",\"major\":true}]}";
    }

    private static String theftCase() {
        return "{\"title\":\"The Vanishing Sapphire\",\"description\":\"The Blue Duchess sapphire disappeared from "
            + "the Lakeview Gallery overnight. The alarm never sounded and the cameras show an empty hall.\","
            + "\"subject\":\"Blue Duchess sapphire\",\"culpritId\":\"owen\",\"keyEvidenceId\":\"jewel_box\","
            + "\"locations\":["
            + "{\"id\":\"gallery\",\"name\":\"Gallery Hall\",\"description\":\"Marble floors and an empty glass "
            + "display case under a spotlight.\",\"restricted\":false},"
            + "{\"id\":\"security\",\"name\":\"Security Office\",\"description\":\"A cramped room of monitors and "
            + "lockers.\",\"restricted\":false},"
            + "{\"id\":\"cafe\",\"name\":\"Gallery Cafe\",\"description\":\"A quiet cafe with a few tables and a "
            + "notice board.\",\"restricted\":false},"
            + "{\"id\":\"storage\",\"name\":\"Storage Room\",\"description\":\"Shelves of crates and wrapped "
            + "paintings behind a keycard door.\",\"restricted\":true,\"unlockEvidenceId\":\"keycard\","
            + "\"requiredReputation\":3},"
            + "{\"id\":\"dock\",\"name\":\"Loading Dock\",\"description\":\"A concrete bay with a dumpster and a "
            + "roller door.\",\"restricted\":false}],"
            + "\"npcs\":["
            + "{\"id\":\"priya\",\"name\":\"Priya Nair\",\"role\":\"restorer\",\"personality\":\"precise and calm\","
            + "\"locationId\":\"gallery\",\"behaviour\":\"truthful\",\"knownFacts\":[\"The display case was opened "
            + "with a key, not forced.\",\"Only security staff carry display case keys.\"],\"secret\":\"She once "
            + "applied for the curator job and was rejected.\"},"
            + "{\"id\":\"owen\",\"name\":\"Owen Brooks\",\"role\":\"night guard\",\"personality\":\"friendly but "
            + "jumpy\",\"locationId\":\"security\",\"behaviour\":\"evasive\",\"knownFacts\":[\"I did my rounds "
            + "every hour.\",\"The cameras glitch sometimes.\"],\"secret\":\"He looped the camera feed, took the "
            + "sapphire and hid it in the storage room.\"},"
            + "{\"id\":\"marcus\",\"name\":\"Marcus Reed\",\"role\":\"wealthy donor\",\"personality\":\"arrogant\","
            + "\"locationId\":\"cafe\",\"behaviour\":\"evasive\",\"knownFacts\":[\"I offered to buy the sapphire "
            + "last month and was refused.\",\"I left the gallery at eight.\"],\"secret\":\"He is nearly "
            + "bankrupt.\"},"
            + "{\"id\":\"lena\",\"name\":\"Lena Ortiz\",\"role\":\"barista\",\"personality\":\"observant\","
            + "\"locationId\":\"dock\",\"behaviour\":\"truthful\",\"knownFacts\":[\"I saw the night guard carrying "
            + "a small box toward storage around two.\",\"The storage keycard is kept in a security locker.\"],"
            + "\"secret\":\"She takes smoke breaks she shouldn't.\"}],"
            + "\"evidence\":["
            + "{\"id\":\"case_lock\",\"name\":\"Unforced lock\",\"description\":\"The display case lock has no "
            + "scratches; it was opened with a key.\",\"locationId\":\"gallery\",\"hiddenIn\":\"display case\","
            + "\"pointsToNpcId\":null,\"major\":false},"
            + "{\"id\":\"camera_log\",\"name\":\"Camera log\",\"description\":\"The log shows a recorded loop "
            + "started at 1:58 using Owen Brooks's login.\",\"locationId\":\"security\",\"hiddenIn\":\"monitor\","
            + "\"pointsToNpcId\":\"owen\",\"major\":true},"
            + "{\"id\":\"keycard\",\"name\":\"Storage keycard\",\"description\":\"A keycard for the storage room.\","
            + "\"locationId\":\"security\",\"hiddenIn\":\"locker\",\"pointsToNpcId\":null,\"major\":false},"
            + "{\"id\":\"flyer\",\"name\":\"Auction flyer\",\"description\":\"A private auction flyer with Marcus "
            + "Reed's notes about gemstones.\",\"locationId\":\"cafe\",\"hiddenIn\":\"notice board\","
            + "\"pointsToNpcId\":\"marcus\",\"major\":false},"
            + "{\"id\":\"gloves\",\"name\":\"Discarded guard gloves\",\"description\":\"Security-issue gloves "
            + "with Owen Brooks's badge number inside.\",\"locationId\":\"dock\",\"hiddenIn\":\"dumpster\","
            + "\"pointsToNpcId\":\"owen\",\"major\":true},"
            + "{\"id\":\"jewel_box\",\"name\":\"Velvet jewel box\",\"description\":\"The Blue Duchess sapphire, "
            + "wrapped in a guard's jacket with the name tag O. Brooks.\",\"locationId\":\"storage\","
            + "\"hiddenIn\":\"crate\",\"pointsToNpcId\":\"owen\",\"major\":true}]}";
    }
}
