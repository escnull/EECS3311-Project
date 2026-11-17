package investigation;

import java.util.List;

import investigation.agent.AgentController;
import investigation.agent.MemoryManager;
import investigation.controller.GameController;
import investigation.llm.LLMClient;
import investigation.llm.OfflineLLMClient;
import investigation.model.Game;

/** Builds fully wired games for tests. */
public final class TestGames {
    private TestGames() { }

    public static GameController offlineMurder(String... attributes) {
        return started(new OfflineLLMClient(), "murder", attributes);
    }

    public static GameController started(LLMClient llm, String type, String... attributes) {
        Game game = new Game();
        MemoryManager memory = new MemoryManager();
        GameController c = new GameController(game, new AgentController(llm, memory), memory);
        c.createInvestigator("Tess Tester", attributes.length == 0 ? List.of("Methodical") : List.of(attributes));
        if (type != null && !c.startGame(type, "")) {
            throw new IllegalStateException("could not start test game");
        }
        return c;
    }
}
