package investigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import investigation.util.Json;

public class JsonTest {
    @Test
    public void roundTripPreservesValues() {
        Map<String, Object> m = Map.of("name", "Line \"one\"\nTwo", "n", 3, "ok", true, "list", List.of("a", "b"));
        @SuppressWarnings("unchecked")
        Map<String, Object> back = (Map<String, Object>) Json.parse(Json.write(m));
        assertEquals("Line \"one\"\nTwo", back.get("name"));
        assertEquals(3, Json.integer(back, "n", 0));
        assertEquals(List.of("a", "b"), Json.strings(back, "list"));
    }

    @Test
    public void extractsObjectFromFencedLlmReply() {
        Map<String, Object> m = Json.extractObject("Sure! Here it is:\n```json\n{\"steps\": [], \"x\": \"}\"}\n```");
        assertEquals("}", m.get("x"));
    }

    @Test
    public void rejectsTextWithoutJson() {
        assertThrows(IllegalArgumentException.class, () -> Json.extractObject("no json here"));
    }
}
