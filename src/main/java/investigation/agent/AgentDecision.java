package investigation.agent;

import java.util.ArrayList;
import java.util.List;

import investigation.command.GameCommand;

/** Result of interpreting free text: a plan of commands, or a question back to the player. */
public class AgentDecision {
    private final List<GameCommand> commands = new ArrayList<>();
    private String clarification;
    private String error;
    private GameCommand pendingConfirmation;
    private boolean lookRequested;
    private String reasoning;

    public static AgentDecision clarify(String question) {
        AgentDecision d = new AgentDecision();
        d.clarification = question;
        return d;
    }

    public static AgentDecision error(String message) {
        AgentDecision d = new AgentDecision();
        d.error = message;
        return d;
    }

    public void addCommand(GameCommand c) { commands.add(c); }
    public List<GameCommand> getCommands() { return commands; }
    public String getClarification() { return clarification; }
    public void setClarification(String c) { clarification = c; }
    public String getError() { return error; }
    public GameCommand getPendingConfirmation() { return pendingConfirmation; }
    public void setPendingConfirmation(GameCommand c) { pendingConfirmation = c; }
    public boolean isLookRequested() { return lookRequested; }
    public void setLookRequested(boolean b) { lookRequested = b; }
    public String getReasoning() { return reasoning; }
    public void setReasoning(String r) { reasoning = r; }
}
