package investigation;

import java.util.Arrays;
import java.util.List;

import investigation.agent.AgentController;
import investigation.agent.MemoryManager;
import investigation.config.AppConfig;
import investigation.controller.GameController;
import investigation.llm.LLMClient;
import investigation.llm.LLMClientFactory;
import investigation.model.Game;
import investigation.ui.GameCLI;
import investigation.ui.GameGUI;

/** Entry point. Starts the GUI by default, or the CLI with --cli. Add --offline to run without an LLM. */
public final class Main {
    private Main() { }

    public static void main(String[] args) {
        List<String> flags = Arrays.asList(args);
        AppConfig config = AppConfig.load();
        if (flags.contains("--offline")) {
            config.set("LLM_PROVIDER", "offline");
        }
        LLMClient llm;
        try {
            llm = LLMClientFactory.create(config);
        } catch (RuntimeException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(1);
            return;
        }
        Game game = new Game();
        MemoryManager memory = new MemoryManager();
        AgentController agent = new AgentController(llm, memory);
        GameController controller = new GameController(game, agent, memory);

        if (flags.contains("--cli")) {
            new GameCLI(controller, System.out).run();
        } else {
            GameGUI.launch(controller);
        }
    }
}
