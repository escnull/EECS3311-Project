package investigation.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader/writer used for LLM responses and save files.
 * Objects become LinkedHashMap, arrays become ArrayList, numbers become Double.
 * Kept dependency-free so the project builds with only a JDK.
 */
public final class Json {

    private Json() { }

    // ------------------------------------------------------------------ parsing

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object value = p.readValue();
        p.skipWs();
        if (p.pos != p.s.length()) {
            throw new IllegalArgumentException("Unexpected trailing characters at " + p.pos);
        }
        return value;
    }

    /**
     * LLMs often wrap JSON in prose or ``` fences. This finds the first balanced
     * JSON object in the text and parses it.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractObject(String text) {
        if (text == null) {
            throw new IllegalArgumentException("No text to parse");
        }
        int start = text.indexOf('{');
        while (start >= 0) {
            int end = findMatching(text, start);
            if (end > start) {
                try {
                    Object o = parse(text.substring(start, end + 1));
                    if (o instanceof Map) {
                        return (Map<String, Object>) o;
                    }
                } catch (IllegalArgumentException ignored) {
                    // try the next candidate
                }
            }
            start = text.indexOf('{', start + 1);
        }
        throw new IllegalArgumentException("No JSON object found in response");
    }

    private static int findMatching(String t, int start) {
        int depth = 0;
        boolean inStr = false;
        for (int i = start; i < t.length(); i++) {
            char c = t.charAt(i);
            if (inStr) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inStr = false;
                }
            } else if (c == '"') {
                inStr = true;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) {
            this.s = s;
        }

        void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        Object readValue() {
            skipWs();
            if (pos >= s.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            char c = s.charAt(pos);
            switch (c) {
                case '{': return readObject();
                case '[': return readArray();
                case '"': return readString();
                case 't': expect("true"); return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null"); return null;
                default: return readNumber();
            }
        }

        void expect(String word) {
            if (!s.startsWith(word, pos)) {
                throw new IllegalArgumentException("Expected " + word + " at " + pos);
            }
            pos += word.length();
        }

        Map<String, Object> readObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // {
            skipWs();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                if (peek() != '"') {
                    throw new IllegalArgumentException("Expected key at " + pos);
                }
                String key = readString();
                skipWs();
                if (peek() != ':') {
                    throw new IllegalArgumentException("Expected ':' at " + pos);
                }
                pos++;
                map.put(key, readValue());
                skipWs();
                char c = peek();
                pos++;
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("Expected ',' or '}' at " + (pos - 1));
                }
            }
        }

        List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipWs();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(readValue());
                skipWs();
                char c = peek();
                pos++;
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("Expected ',' or ']' at " + (pos - 1));
                }
            }
        }

        String readString() {
            StringBuilder sb = new StringBuilder();
            pos++; // opening quote
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char e = s.charAt(pos++);
                    switch (e) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                            break;
                        default: sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        Double readNumber() {
            int start = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("Unexpected character '" + s.charAt(pos) + "' at " + pos);
            }
            return Double.parseDouble(s.substring(start, pos));
        }

        char peek() {
            if (pos >= s.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            return s.charAt(pos);
        }
    }

    // ------------------------------------------------------------------ writing

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb, 0);
        return sb.toString();
    }

    private static void write(Object v, StringBuilder sb, int indent) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            quote((String) v, sb);
        } else if (v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                sb.append((long) d);
            } else {
                sb.append(d);
            }
        } else if (v instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) v;
            if (m.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                pad(sb, indent + 1);
                quote(String.valueOf(e.getKey()), sb);
                sb.append(": ");
                write(e.getValue(), sb, indent + 1);
                if (++i < m.size()) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            pad(sb, indent);
            sb.append('}');
        } else if (v instanceof Iterable) {
            List<Object> items = new ArrayList<>();
            ((Iterable<?>) v).forEach(items::add);
            if (items.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            for (int i = 0; i < items.size(); i++) {
                pad(sb, indent + 1);
                write(items.get(i), sb, indent + 1);
                if (i < items.size() - 1) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            pad(sb, indent);
            sb.append(']');
        } else {
            quote(v.toString(), sb);
        }
    }

    private static void pad(StringBuilder sb, int n) {
        for (int i = 0; i < n; i++) {
            sb.append("  ");
        }
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------ typed getters

    public static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public static String str(Map<String, Object> m, String key, String def) {
        String v = str(m, key);
        return v == null || v.isBlank() || v.equals("null") ? def : v;
    }

    public static int integer(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v instanceof String) {
            try {
                return Integer.parseInt(((String) v).trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    public static boolean bool(Map<String, Object> m, String key, boolean def) {
        Object v = m.get(key);
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if (v instanceof String) {
            return Boolean.parseBoolean((String) v);
        }
        return def;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> objects(Map<String, Object> m, String key) {
        List<Map<String, Object>> out = new ArrayList<>();
        Object v = m.get(key);
        if (v instanceof List) {
            for (Object o : (List<Object>) v) {
                if (o instanceof Map) {
                    out.add((Map<String, Object>) o);
                }
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static List<String> strings(Map<String, Object> m, String key) {
        List<String> out = new ArrayList<>();
        Object v = m.get(key);
        if (v instanceof List) {
            for (Object o : (List<Object>) v) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
        }
        return out;
    }
}
