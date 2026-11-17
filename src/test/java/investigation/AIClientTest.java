package investigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import investigation.llm.AIClient;
import investigation.llm.LLMException;

/** Checks the Anthropic client against a local fake server (no real key or network needed). */
public class AIClientTest {

    private HttpServer server(int status, String body, AtomicReference<String> seenKey, AtomicReference<String> seenBody)
            throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/v1/messages", ex -> {
            seenKey.set(ex.getRequestHeaders().getFirst("x-api-key") + "|" + ex.getRequestHeaders().getFirst("anthropic-version"));
            seenBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        s.start();
        return s;
    }

    @Test
    public void sendsMessagesRequestAndCountsTokens() throws Exception {
        AtomicReference<String> key = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer s = server(200, "{\"content\":[{\"type\":\"text\",\"text\":\"Hello detective\"}],"
                + "\"usage\":{\"input_tokens\":12,\"output_tokens\":3}}", key, body);
        try {
            AIClient c = new AIClient("test-key", "claude-test", "http://127.0.0.1:" + s.getAddress().getPort(), 100, 10);
            assertEquals("Hello detective", c.generate("sys", "hi"));
            assertEquals("test-key|2023-06-01", key.get());
            assertTrue(body.get().contains("\"model\": \"claude-test\""));
            assertEquals(12, c.getUsage().getInputTokens());
        } finally {
            s.stop(0);
        }
    }

    @Test
    public void httpErrorBecomesLlmException() throws Exception {
        HttpServer s = server(401, "{\"error\":{\"message\":\"invalid x-api-key\"}}",
                new AtomicReference<>(), new AtomicReference<>());
        try {
            AIClient c = new AIClient("bad", "claude-test", "http://127.0.0.1:" + s.getAddress().getPort(), 100, 10);
            LLMException e = assertThrows(LLMException.class, () -> c.generate("sys", "hi"));
            assertTrue(e.getMessage().contains("401"));
        } finally {
            s.stop(0);
        }
    }
}
