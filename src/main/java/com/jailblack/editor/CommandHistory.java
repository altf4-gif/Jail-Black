package com.jailblack.editor;

import java.util.ArrayList;
import java.util.List;

public class CommandHistory {
    private final List<Command> history = new ArrayList<>();
    private int currentIndex = -1;

    public void execute(Command command) {
        if (currentIndex < history.size() - 1) {
            history.subList(currentIndex + 1, history.size()).clear();
        }
        command.execute();
        history.add(command);
        currentIndex++;
    }

    public void undo() {
        if (currentIndex >= 0) {
            history.get(currentIndex).undo();
            currentIndex--;
        }
    }

    public void redo() {
        if (currentIndex < history.size() - 1) {
            currentIndex++;
            history.get(currentIndex).execute();
        }
    }

    public boolean canUndo() { return currentIndex >= 0; }
    public boolean canRedo() { return currentIndex < history.size() - 1; }

    public void clear() { history.clear(); currentIndex = -1; }
    public int getHistorySize() { return history.size(); }
    public int getCurrentIndex() { return currentIndex; }
}
