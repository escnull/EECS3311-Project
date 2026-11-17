# Stage 2 report notes (source material for stage2-report.pdf)

Everything below describes the code as submitted. Copy, trim, and reword it for the report.

## 1. Overview and status

Java 17 Swing GUI + CLI detective game. Both interfaces call one `GameController`.
The AI agent (`AgentController`) uses an LLM through the `LLMClient` interface:
Anthropic Claude (`AIClient`, default model `claude-haiku-4-5`) or any OpenAI-compatible
API (`OpenAICompatibleClient`). The agent never changes `Game` directly: it produces
commands, dialogue, narrative and leads, and the deterministic game system validates all of them.

**Agent behaviour (beyond prompt forwarding):**
- *Planning / multi-step execution*: free text becomes a plan of up to 3 commands, executed in order and stopped at the first rejected step (`interpretAction`).
- *Tool use*: the agent's only way to act is through the game's commands (move, search, question, accuse), and each one is validated first.
- *Retrieval and memory*: `MemoryManager` observes the game and returns the most relevant past events for each prompt.
- *Self-checking*: cases are validated and regenerated (up to 3 attempts); dialogue that leaks forbidden facts is regenerated (up to 3), then a fallback line is used; narrative that reveals the culprit early is rejected; lead suggestions that reference unknown evidence are rejected.
- *Decision making with guard rails*: an accusation from free text is never executed automatically; the player must confirm it.

**Known limitations (state honestly):** only two case types; the map has no adjacency (any unlocked location is reachable); the fact checker is keyword-based and can miss paraphrased leaks; the GUI is functional rather than polished; `--offline` mode uses a rule-based stand-in with two fixed sample cases and is for testing only.

## 2. Feature mapping

| ID | Feature | Status | Main classes (role) | Key methods | Patterns | Type |
|---|---|---|---|---|---|---|
| F01 | Player Creation | Implemented | `GameGUI`/`GameCLI` (input), `GameController` (validates), `Investigator` (profile, attributes), `Game` (stores, notifies) | `createInvestigator`, `Investigator.validate`, `setInvestigator`, `getInfo` | Observer | Deterministic |
| F02 | Dynamic Case Generation | Implemented | `AgentController` (chooses creator), `CaseCreator`/`MurderCaseCreator`/`TheftCaseCreator` (prompt, parse, objectives), `CaseValidator` (consistency), `LLMClient` | `startGame`, `generateCase`, `createCase`, `generateValidated`, `CaseValidator.validate`, `setCase`, `updateState` | Factory Method, State | Hybrid |
| F03 | World and Location Exploration | Implemented | `MoveCommand`, `Location` (access rules), `AgentController.describeArrival` (AI narration), `GameGUI` map | `executeCommand`, `validate`, `canAccess`, `describeArrival` | Command, State | Hybrid |
| F04 | Natural Language Actions | Modified (multi-step) | `AgentController` (plans), `PromptBuilder` (visible state only), `MemoryManager`, `AgentDecision` | `processInput`, `interpretAction`, `getRelevantHistory` | Command | Hybrid |
| F05 | NPC Interaction and Dialogue | Implemented | `QuestionNpcCommand`, `NPC`, `AgentStrategy`/`TruthfulStrategy`/`EvasiveStrategy`, `FactChecker` | `generateDialogue`, `getStrategy`, `execute`, `forbiddenFacts`, `updateRelationship` | Strategy, Command | Hybrid |
| F06 | Evidence Collection | Implemented | `SearchCommand` (matching, rewards), `Evidence`, `Case` | `execute`, `markFound`, `getDetails` | Command, Observer | Hybrid (AI-written case content, deterministic discovery) |
| F07 | Investigation and Lead Management | Implemented | `AgentController.suggestLeads`, `Lead`, `Case.addLead`, GUI Leads tab | `suggestLeads`, `addLead`, `setLeadStatus` | Observer | Hybrid |
| F08 | Rule and Action Validation | Implemented | `AbstractCommand` (state + rule checks), `GameState`, `Location` | `validate`, `handleAction`, `checkRules`, `canAccess` | Command, State | Deterministic |
| F09 | Consequence Generation | Implemented | `CommandOutcome` (deterministic changes), `GameController.respond` (relationship/strategy changes), `AgentController.generateConsequence` | `executeCommand`, `generateConsequence` | State, Observer | Hybrid |
| F10 | Story and Investigation Memory | Implemented | `MemoryManager` (observer, retrieval), `GameEvent` | `update`, `getRelevantHistory`, `asPromptContext` | Observer | Hybrid |
| F11 | Case Progress Tracking | Implemented | `Objective` (self-evaluating), `Game.updateState`, `AgentController.objectivesForLeads` | `evaluate`, `updateState`, `objectivesForLeads` | State | Hybrid |
| F12 | Save and Load Game | Implemented | `Game` (JSON save with atomic write, validated load), `GameController` | `saveGame`, `loadGame`, `save`, `load` | Observer | Deterministic |
| F13 | Accuse Suspect / Close Case | **New** | `AccuseCommand`, `Case`, `SolvedState` | `execute`, `countFoundEvidenceAgainst`, `updateStatus` | Command, State | Deterministic |

## 3. Patterns (all five from Stage 1, plus one extra)

1. **Command**: `GameCommand` (interface) with `MoveCommand`, `SearchCommand`, `QuestionNpcCommand`, `AccuseCommand`; invoker `GameController.executeCommand`; receiver `Game`; client `AgentController.interpretAction`. Every request is validated before execution, which keeps the AI from changing state directly.
2. **State**: `GameState` with `NoCaseState`, `InvestigatingState`, `SolvedState`; context `Game`. Each state answers `handleAction(ActionType)` and decides its successor in `next(Game)`, called from `Game.updateState()`.
3. **Strategy**: `AgentStrategy` with `TruthfulStrategy`, `EvasiveStrategy`; context `NPC`, used by `AgentController.generateDialogue`. Strategies decide which facts may be revealed. They change at runtime: an innocent evasive NPC becomes truthful at relationship 3 or above, and a truthful NPC turns evasive at -3 or below (`GameController.respond`).
4. **Factory Method**: `CaseCreator.createCase` (abstract) implemented by `MurderCaseCreator` and `TheftCaseCreator`; product `Case`; client `AgentController.generateCase` holds a registry of creators (`register`).
5. **Observer**: subject `Game` (`attach`, `notifyObservers`), observer interface `GameObserver`, observers `GameGUI`, `GameCLI`, `MemoryManager`; data `GameEvent`.
6. *(Extra)* **Template Method**: `AbstractCommand.validate` fixes the order (state check, then investigator check, then `checkRules` hook); `CaseCreator.generateValidated` fixes ask → parse → validate → retry.

## 4. Changes from Stage 1 (Original -> Implemented -> Reason -> Impact)

| Original design | Implemented change | Reason | Impact |
|---|---|---|---|
| No way to finish a case (SolvedState had no trigger) | New F13 `AccuseCommand`; a case ends SOLVED (correct + at least 2 supporting evidence) or FAILED (2 wrong accusations or abandon) | `SolvedState` and the "solve the case" objective needed a player action | New command, new use case "Accuse Suspect"; State and Command patterns extended |
| `interpretAction(input): GameCommand` | Returns `AgentDecision` (up to 3 commands, clarification, or accusation awaiting confirmation) | Players write compound requests; multi-step planning is stronger agent behaviour | F04, SD02 updated; `GameController.processInput` loops over the plan |
| `AgentStrategy.execute(context: String): String` | `execute(DialogueContext)`, plus `forbiddenFacts` and `fallbackLine` | The strategy needs the NPC, player and evidence to decide; the fact checker needs the forbidden list | F05, SD03 |
| `GameState.handleAction(): void` | `handleAction(ActionType): boolean`, `getRejectionReason`, `next(Game)` | States must answer whether a specific action is allowed and own their transitions | F08, F11 |
| `GameCommand.execute(game): void` | Returns `CommandOutcome` (message, deterministic changes, major-decision flag, NPC to answer) | The controller needs to know which AI follow-up to run without `instanceof` checks | F09 consequence pipeline |
| `Location.canAccess(player)` | `canAccess(player, case)` | Access depends on whether the unlock evidence has been found, or on reputation | F03 |
| `CaseCreator.createCase()` | `createCase(LLMClient, preferences)` | Creators need the LLM and the player's preferences | F02 |
| `Game.save(file)` / `load(file)` | `save(file, history)` / `load(file)` returns history | Game does not depend on `MemoryManager`; the controller passes history in and out | F12 |
| GameCLI was not an observer | `GameCLI` also implements `GameObserver` | Same live output in both interfaces | Observer has 3 observers |
| Only Claude | `LLMClient` also implemented by `OpenAICompatibleClient` and `OfflineLLMClient` | Lets graders use free models; allows testing without a key | No change to the rest of the system |
| (none) | New support classes: `PromptBuilder`, `FactChecker`, `CaseValidator`, `AppConfig`, `LLMClientFactory`, `EventFormatter`, `AbstractCommand`, `Json` | Validation of AI output, safe config, shared formatting, no external libraries | Documented in the updated class diagram |

## 5. AI tools log (fill in your own numbers)

| Tool | Model | Used for | Tokens / cost |
|---|---|---|---|
| Claude (claude.ai) | Claude Opus 5.5 | Generated the Stage 2 implementation from the Stage 1 report: all packages, tests, README; reviewed and tested by me | (check your usage page) |
| Game runtime LLM | claude-haiku-4-5 (or your choice) | Case generation, action interpretation, dialogue, narration, leads | Shown live: GUI status bar or CLI `usage` |

## 6. Verification performed

- 27 unit tests (`mvn test`): command validation, state transitions, restricted access, search matching, accusations, strategy switching, save/load round trip, corrupted save rejection, memory relevance, case regeneration after invalid output, plan building, clarification, accusation confirmation, LLM failure leaving state unchanged, secret-leak rejection, lead validation, spoiler rejection, Claude client request and error handling.
- Scripted end-to-end CLI playthroughs of both case types (offline mode) and a GUI smoke test.
- Defects found and fixed during development: the CLI `go` command swallowed multi-step sentences (now routed to the agent); the change log printed before the action result; map buttons stretched to fill the panel.

## 7. Demo video script (one segment per feature, record GUI and CLI)

Do not show your `.env`. Record with a real LLM.

1. **F01** GUI: Game > New Investigator. First submit an empty name to show the error, then create one.
2. **F02** GUI: New Investigation with type and preferences, show the briefing and objectives. CLI: `new theft`.
3. **F03** Click a locked location (show the reason), then an open one (show the AI narration).
4. **F04** Type "go to the kitchen and search the drawer" (multi-step plan), then something unclear like "do the thing" (clarification).
5. **F05** Ask a truthful NPC and an evasive NPC the same question. Ask something rude and show the attitude change.
6. **F06** Search a listed object (found), search it again (nothing new), search something wrong.
7. **F07** Leads tab: Suggest Connections; confirm or dismiss a lead.
8. **F08** Try to question someone not present, or accuse with no evidence.
9. **F09** Find key evidence or enter the restricted area and show the `[Changes]` line and `>>` narrative.
10. **F10** History tab: filter by a person's name. CLI: `history <name>`.
11. **F11** Objectives tab before and after an action; show an agent-suggested objective.
12. **F12** Save, make a move, Load to show the restore; then try to load a missing file.
13. **F13** Make a wrong accusation (penalty, NPC refuses to talk), then the correct one (case solved, state changes).
