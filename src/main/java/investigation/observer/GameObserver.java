package investigation.observer;

import investigation.model.GameEvent;

/** Observer pattern: anything that must react to game changes (GUI, CLI, memory). */
public interface GameObserver {
    void update(GameEvent event);
}
