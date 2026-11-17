package investigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import investigation.agent.AgentController;
import investigation.agent.MemoryManager;
import investigation.command.AccuseCommand;
import investigation.command.MoveCommand;
import investigation.command.QuestionNpcCommand;
import investigation.command.SearchCommand;
import investigation.controller.GameController;
import investigation.llm.OfflineLLMClient;
import investigation.model.CaseStatus;
import investigation.model.Game;
import investigation.model.Objective;

public class CommandAndStateTest {

    @Test
    public void commandsAreRejectedWhenNoCaseIsActive() {
        Game game = new Game();
        MemoryManager m = new MemoryManager();
        new GameController(game, new AgentController(new OfflineLLMClient(), m), m)
                .createInvestigator("Al", List.of("Perceptive"));
        MoveCommand move = new MoveCommand("study");
        assertFalse(move.validate(game));
        assertEquals("No Case", game.getState().getStateName());
    }

    @Test
    public void startingACaseMovesToInvestigatingState() {
        GameController c = TestGames.offlineMurder();
        assertEquals("Investigating", c.getGame().getState().getStateName());
        assertEquals("study", c.getGame().getCurrentLocation().getId());
    }

    @Test
    public void restrictedLocationNeedsItsKey() {
        GameController c = TestGames.offlineMurder();
        Game g = c.getGame();
        MoveCommand toCellar = new MoveCommand("cellar");
        assertFalse(toCellar.validate(g));
        assertEquals("study", g.getCurrentLocation().getId());

        assertTrue(c.move("kitchen"));
        assertTrue(c.search("drawer"));
        assertTrue(g.getCase().getEvidence("cellar_key").isFound());
        assertTrue(new MoveCommand("cellar").validate(g));
    }

    @Test
    public void searchOnlyFindsMatchingEvidenceOnce() {
        GameController c = TestGames.offlineMurder("Perceptive");
        Game g = c.getGame();
        c.search("bookcase");
        assertEquals(0, g.getCase().getFoundEvidence().size());
        c.search("desk");
        assertEquals(1, g.getCase().getFoundEvidence().size());
        c.search("desk");
        assertEquals(1, g.getCase().getFoundEvidence().size());
    }

    @Test
    public void methodicalInvestigatorCanSweepARoom() {
        GameController c = TestGames.offlineMurder("Methodical");
        c.search("room");
        assertEquals(2, c.getGame().getCase().getFoundEvidence().size());
    }

    @Test
    public void questioningSomeoneElsewhereIsRejected() {
        GameController c = TestGames.offlineMurder();
        QuestionNpcCommand q = new QuestionNpcCommand("martha", "Hello?");
        assertFalse(q.validate(c.getGame()));
        assertTrue(q.getRejectionReason().contains("not here"));
    }

    @Test
    public void accusationNeedsEvidence() {
        GameController c = TestGames.offlineMurder();
        assertFalse(new AccuseCommand("clara").validate(c.getGame()));
    }

    @Test
    public void correctAccusationWithEvidenceSolvesTheCase() {
        GameController c = TestGames.offlineMurder();
        c.search("desk");
        c.search("fireplace");
        assertTrue(c.accuse("clara"));
        assertEquals(CaseStatus.SOLVED, c.getCase().getStatus());
        assertEquals("Case Closed", c.getGame().getState().getStateName());
        assertFalse(new SearchCommand("desk").validate(c.getGame()));
        assertTrue(c.getCase().getObjectives().stream()
                .anyMatch(o -> o.getType() == Objective.Type.SOLVE_CASE && o.isCompleted()));
    }

    @Test
    public void twoWrongAccusationsFailTheCase() {
        GameController c = TestGames.offlineMurder();
        c.search("desk");
        c.accuse("edmund");
        assertEquals(CaseStatus.OPEN, c.getCase().getStatus());
        assertTrue(c.getCase().getNpc("edmund").refusesToTalk());
        c.accuse("felix");
        assertEquals(CaseStatus.FAILED, c.getCase().getStatus());
        assertEquals("Case Closed", c.getGame().getState().getStateName());
    }

    @Test
    public void rudeQuestionsCanTurnATruthfulNpcEvasive() {
        GameController c = TestGames.offlineMurder("Perceptive");
        c.question("edmund", "You killed him, liar!");
        c.question("edmund", "Confess, you liar!");
        assertEquals("evasive", c.getCase().getNpc("edmund").getStrategy().getName());
    }
}
