package investigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import investigation.agent.MemoryManager;
import investigation.controller.GameController;
import investigation.model.EventType;
import investigation.model.GameEvent;

public class PersistenceAndMemoryTest {

    @Test
    public void saveAndLoadRestoresTheInvestigation() throws Exception {
        Path file = Files.createTempFile("save", ".json");
        GameController c = TestGames.offlineMurder();
        c.search("desk");
        c.move("kitchen");
        c.question("martha", "What did you see?");
        assertTrue(c.saveGame(file.toString()));

        GameController fresh = TestGames.started(new investigation.llm.OfflineLLMClient(), null);
        assertTrue(fresh.loadGame(file.toString()));
        assertEquals("kitchen", fresh.getGame().getCurrentLocation().getId());
        assertTrue(fresh.getCase().getEvidence("teacup").isFound());
        assertEquals(1, fresh.getCase().getNpc("martha").getInterviewCount());
        assertEquals("Investigating", fresh.getGame().getState().getStateName());
        assertFalse(fresh.getHistory("").isEmpty());
    }

    @Test
    public void corruptedSaveIsRejectedAndGameIsUnchanged() throws Exception {
        Path file = Files.createTempFile("bad", ".json");
        Files.writeString(file, "{\"version\": 1, \"case\": {\"title\": \"x\"}}");
        GameController c = TestGames.offlineMurder();
        c.move("garden");
        assertFalse(c.loadGame(file.toString()));
        assertEquals("garden", c.getGame().getCurrentLocation().getId());
        assertFalse(c.loadGame(file.resolveSibling("does-not-exist.json").toString()));
    }

    @Test
    public void memoryReturnsRelevantEventsInTimeOrder() {
        MemoryManager m = new MemoryManager();
        m.update(new GameEvent(EventType.MOVED, "Moved to Kitchen", "kitchen", 1));
        m.update(new GameEvent(EventType.EVIDENCE_FOUND, "Torn glove: embroidered C.W.", "torn_glove", 2));
        m.update(new GameEvent(EventType.DIALOGUE, "Martha said she saw a glove", "martha", 3));
        m.update(new GameEvent(EventType.MESSAGE, "glove glove glove", null, 4));
        List<GameEvent> r = m.getRelevantHistory("where was the glove", 5);
        assertEquals(2, r.size());
        assertEquals(2, r.get(0).getTurn());
        assertEquals(3, r.get(1).getTurn());
    }
}
