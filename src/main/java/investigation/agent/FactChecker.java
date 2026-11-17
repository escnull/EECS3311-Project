package investigation.agent;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Deterministic checks that reject AI text which breaks established rules or facts. */
public final class FactChecker {
    private static final Pattern CONFESSION = Pattern.compile(
            "\\bi (killed|murdered|stole|poisoned|did it|took it|confess)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> STOP = Set.of("there", "their", "about", "which", "would", "could",
            "should", "where", "after", "before", "never", "always", "because", "while");

    private FactChecker() { }

    /** True if the text reveals any forbidden fact (most of its key words appear) or is a confession. */
    public static boolean leaks(String text, List<String> forbiddenFacts) {
        if (CONFESSION.matcher(text).find()) {
            return true;
        }
        Set<String> words = contentWords(text);
        for (String fact : forbiddenFacts) {
            Set<String> factWords = contentWords(fact);
            if (factWords.size() < 2) {
                continue;
            }
            int hit = 0;
            for (String w : factWords) {
                if (words.contains(w)) {
                    hit++;
                }
            }
            if (hit >= 3 && hit >= Math.ceil(factWords.size() * 0.6)) {
                return true;
            }
        }
        return false;
    }

    /** True if the text names the culprit together with guilt words before the case is solved. */
    public static boolean revealsCulprit(String text, String culpritName) {
        if (culpritName == null || culpritName.isBlank()) {
            return false;
        }
        String t = text.toLowerCase(Locale.ROOT);
        boolean named = false;
        for (String part : culpritName.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (part.length() >= 3 && t.contains(part)) {
                named = true;
                break;
            }
        }
        return named && t.matches("(?s).*\\b(guilty|killer|murderer|thief|culprit|did it|confess\\w*)\\b.*");
    }

    static Set<String> contentWords(String s) {
        Set<String> out = new HashSet<>();
        for (String w : s.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.length() >= 5 && !STOP.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }
}
