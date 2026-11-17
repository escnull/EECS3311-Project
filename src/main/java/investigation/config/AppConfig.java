package investigation.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads local settings from a .env file (never committed) and environment variables.
 * Environment variables win over the file.
 */
public class AppConfig {
    private final Map<String, String> values = new HashMap<>();

    public static AppConfig load() {
        return load(Path.of(".env"));
    }

    public static AppConfig load(Path envFile) {
        AppConfig c = new AppConfig();
        if (Files.exists(envFile)) {
            try {
                for (String line : Files.readAllLines(envFile)) {
                    String t = line.trim();
                    if (t.isEmpty() || t.startsWith("#") || !t.contains("=")) {
                        continue;
                    }
                    String key = t.substring(0, t.indexOf('=')).trim();
                    String value = t.substring(t.indexOf('=') + 1).trim();
                    if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                            || value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    c.values.put(key, value);
                }
            } catch (IOException e) {
                System.err.println("Warning: could not read " + envFile + ": " + e.getMessage());
            }
        }
        System.getenv().forEach((k, v) -> {
            if (k.startsWith("LLM_")) {
                c.values.put(k, v);
            }
        });
        return c;
    }

    public void set(String key, String value) {
        values.put(key, value);
    }

    public String get(String key, String def) {
        String v = values.get(key);
        return v == null || v.isBlank() ? def : v;
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public String provider() {
        return get("LLM_PROVIDER", "anthropic").toLowerCase();
    }

    /** True if the key looks like the placeholder from .env.example. */
    public boolean hasRealKey() {
        String k = get("LLM_API_KEY", "");
        return !k.isEmpty() && !k.startsWith("YOUR_");
    }
}
