package investigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import investigation.agent.AgentController;
import investigation.agent.AgentDecision;
import investigation.agent.LeadSuggestion;
import investigation.agent.MemoryManager;
import investigation.casegen.CaseGenerationException;
import investigation.casegen.MurderCaseCreator;
import investigation.command.MoveCommand;
import investigation.command.SearchCommand;
import investigation.controller.GameController;
import investigation.llm.OfflineLLMClient;
import investigation.model.Game;
import investigation.model.NPC;

public class AgentTest {

    private static String validCase() throws Exception {
        return new OfflineLLMClient().generate("TASK: GENERATE_CASE\nCRIME TYPE: murder");
    }

    @Test
    public void caseGenerationRetriesAfterAnInvalidCase() throws Exception {
        ScriptedLLM llm = new ScriptedLLM("{\"title\": \"Broken\", \"locations\": []}", validCase());
        assertNotNull(new MurderCaseCreator().createCase(llm, ""));
        assertEquals(2, llm.prompts.size());
        assertTrue(llm.prompts.get(1).contains("was rejected because"));
    }

    @Test
    public void caseGenerationGivesUpAfterThreeAttempts() {
        ScriptedLLM llm = new ScriptedLLM("not json at all");
        assertThrows(CaseGenerationException.class, () -> new MurderCaseCreator().createCase(llm, ""));
        assertEquals(3, llm.prompts.size());
    }

    private GameController withScriptedAgent(ScriptedLLM agentLlm) {
        GameController base = TestGames.offlineMurder();
        Game game = base.getGame();
        MemoryManager memory = new MemoryManager();
        return new GameController(game, new AgentController(agentLlm, memory), memory);
    }

    @Test
    public void interpretActionBuildsAMultiStepPlan() {
        ScriptedLLM llm = new ScriptedLLM("{\"reasoning\":\"x\",\"steps\":[{\"action\":\"move\",\"target\":\"Kitchen\"},"
                + "{\"action\":\"search\",\"target\":\"drawer\"}],\"clarification\":null}");
        GameController c = withScriptedAgent(llm);
        AgentDecision d = c.getAgentController().interpretAction("go to the kitchen and check the drawer", c.getGame());
        assertEquals(2, d.getCommands().size());
        assertTrue(d.getCommands().get(0) instanceof MoveCommand);
        assertTrue(d.getCommands().get(1) instanceof SearchCommand);
        assertTrue(llm.prompts.get(0).contains("LOCATION [kitchen] Kitchen"));
        // hidden information must never be sent to the LLM
        assertTrue(!llm.prompts.get(0).contains("Bitter teacup"));
    }

    @Test
    public void unknownTargetLeadsToClarificationNotACommand() {
        ScriptedLLM llm = new ScriptedLLM("{\"steps\":[{\"action\":\"move\",\"target\":\"the moon\"}]}");
        GameController c = withScriptedAgent(llm);
        AgentDecision d = c.getAgentController().interpretAction("fly to the moon", c.getGame());
        assertTrue(d.getCommands().isEmpty());
        assertNotNull(d.getClarification());
    }

    @Test
    public void accusationFromFreeTextNeedsConfirmation() {
        ScriptedLLM llm = new ScriptedLLM("{\"steps\":[{\"action\":\"accuse\",\"target\":\"clara\"}]}");
        GameController c = withScriptedAgent(llm);
        AgentDecision d = c.getAgentController().interpretAction("arrest Clara", c.getGame());
        assertTrue(d.getCommands().isEmpty());
        assertNotNull(d.getPendingConfirmation());
    }

    @Test
    public void llmFailureLeavesStateUnchanged() {
        ScriptedLLM llm = new ScriptedLLM();
        GameController c = withScriptedAgent(llm);
        c.processInput("go to the kitchen");
        assertEquals("study", c.getGame().getCurrentLocation().getId());
    }

    @Test
    public void dialogueThatLeaksASecretIsReplacedByTheFallback() {
        ScriptedLLM llm = new ScriptedLLM("Fine, I bought arsenic and poured it into my uncle's tea to stop him "
                + "changing the will.");
        GameController c = withScriptedAgent(llm);
        Game g = c.getGame();
        NPC clara = g.getCase().getNpc("clara");
        String reply = c.getAgentController().generateDialogue(g, clara, "Did you poison him?");
        assertEquals(clara.getStrategy().fallbackLine(null), reply);
        assertEquals(3, llm.prompts.size());
    }

    @Test
    public void leadsReferringToUnknownEvidenceAreRejected() {
        ScriptedLLM llm = new ScriptedLLM("{\"leads\":[{\"description\":\"good\",\"evidenceIds\":[\"teacup\"],"
                + "\"npcId\":\"clara\"},{\"description\":\"made up\",\"evidenceIds\":[\"poison_receipt\"],"
                + "\"npcId\":\"clara\"}]}");
        GameController c = withScriptedAgent(llm);
        c.getGame().getCase().getEvidence("teacup").markFound(1);
        LeadSuggestion s = c.getAgentController().suggestLeads(c.getGame());
        assertEquals(1, s.getLeads().size());
        assertEquals("good", s.getLeads().get(0).getDescription());
    }

    @Test
    public void consequenceThatRevealsTheCulpritFallsBackToPlainText() {
        ScriptedLLM llm = new ScriptedLLM("Everyone now knows Clara Whitmore is guilty.");
        GameController c = withScriptedAgent(llm);
        investigation.command.CommandOutcome o = new investigation.command.CommandOutcome("x")
                .major("found something").change("Reputation +1");
        String text = c.getAgentController().generateConsequence(c.getGame(), o);
        assertTrue(text.startsWith("Consequence:"));
    }
}
