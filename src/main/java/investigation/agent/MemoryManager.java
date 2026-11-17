package investigation.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import investigation.model.EventType;
import investigation.model.GameEvent;
import investigation.observer.GameObserver;

/**
 * Story memory (F10). Observes Game, keeps important events, and retrieves the events
 * most relevant to a query so only useful history is placed in LLM prompts.
 */
public class MemoryManager implements GameObserver {
    private static final Set<String> STOP = Set.of("the", "and", "you", "was", "for", "with", "that", "this",
            "what", "where", "who", "did", "have", "has", "are", "from", "about", "your", "asked", "into");

    private final List<GameEvent> history = new ArrayList<>();

    @Override
    public synchronized void update(GameEvent event) {
        if (event.getType() == EventType.CASE_STARTED) {
            history.clear();
        }
        if (event.getType().isMemorable()) {
            addEvent(event);
        }
    }

    public synchronized void addEvent(GameEvent event) {
        history.add(event);
    }

    /** All stored events in time order. */
    public synchronized List<GameEvent> getAll() {
        return new ArrayList<>(history);
    }

    public List<GameEvent> getRelevantHistory(String query) {
        return getRelevantHistory(query, 8);
    }

    /**
     * Scores each event by keyword overlap with the query, importance, and recency,
     * then returns the best {@code limit} events in chronological order.
     * A blank query returns the most recent events.
     */
    public synchronized List<GameEvent> getRelevantHistory(String query, int limit) {
        Set<String> q = keywords(query);
        int n = history.size();
        if (q.isEmpty()) {
            return new ArrayList<>(history.subList(Math.max(0, n - limit), n));
        }
        List<int[]> scored = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            GameEvent e = history.get(i);
            Set<String> words = keywords(e.getDetails() + " " + (e.getSubjectId() == null ? "" : e.getSubjectId()));
            int overlap = 0;
            for (String w : q) {
                if (words.contains(w)) {
                    overlap++;
                }
            }
            if (overlap == 0) {
                continue;
            }
            int score = overlap * 3;
            if (e.getType() == EventType.EVIDENCE_FOUND || e.getType() == EventType.ACCUSATION
                    || e.getType() == EventType.CONSEQUENCE) {
                score += 2;
            }
            if (i >= n - 5) {
                score += 1;
            }
            scored.add(new int[] {i, score});
        }
        return scored.stream()
                .sorted(Comparator.comparingInt((int[] a) -> -a[1]).thenComparingInt(a -> -a[0]))
                .limit(limit)
                .sorted(Comparator.comparingInt(a -> a[0]))
                .map(a -> history.get(a[0]))
                .collect(Collectors.toList());
    }

    /** Text block for prompts. */
    public String asPromptContext(String query, int limit) {
        List<GameEvent> events = getRelevantHistory(query, limit);
        if (events.isEmpty()) {
            return "(nothing relevant yet)";
        }
        return events.stream().map(e -> "- turn " + e.getTurn() + " " + e.getType() + ": " + e.getDetails())
                .collect(Collectors.joining("\n"));
    }

    public synchronized void clear() {
        history.clear();
    }

    public synchronized void restore(List<GameEvent> events) {
        history.clear();
        history.addAll(events);
    }

    static Set<String> keywords(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) {
            return out;
        }
        for (String w : text.toLowerCase(Locale.ROOT).split("[^a-z0-9_]+")) {
            if (w.length() >= 3 && !STOP.contains(w)) {
                out.add(w.endsWith("s") && w.length() > 4 ? w.substring(0, w.length() - 1) : w);
                for (String part : w.split("_")) {
                    if (part.length() >= 3) {
                        out.add(part);
                    }
                }
            }
        }
        return out;
    }
}
