package dev.railroadide.railroad.ide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public final class WorkspaceTabNavigationHistory {
    public static final int MAX_ENTRIES_PER_MODE = 100;

    private final Map<String, Cursor> cursors = new LinkedHashMap<>();

    public void visit(String modeId, String tabId) {
        String normalizedModeId = normalize(modeId);
        String normalizedTabId = normalize(tabId);
        if (normalizedModeId == null || normalizedTabId == null)
            return;

        Cursor cursor = cursors.computeIfAbsent(normalizedModeId, _ -> new Cursor());
        if (cursor.current().filter(normalizedTabId::equals).isPresent())
            return;

        if (cursor.index + 1 < cursor.entries.size()) {
            cursor.entries.subList(cursor.index + 1, cursor.entries.size()).clear();
        }
        cursor.entries.add(normalizedTabId);
        cursor.index = cursor.entries.size() - 1;
        while (cursor.entries.size() > MAX_ENTRIES_PER_MODE) {
            cursor.entries.removeFirst();
            cursor.index--;
        }
    }

    public Optional<String> back(String modeId, Predicate<String> available) {
        return move(modeId, -1, available);
    }

    public Optional<String> forward(String modeId, Predicate<String> available) {
        return move(modeId, 1, available);
    }

    public boolean canGoBack(String modeId, Predicate<String> available) {
        return canMove(modeId, -1, available);
    }

    public boolean canGoForward(String modeId, Predicate<String> available) {
        return canMove(modeId, 1, available);
    }

    public IDELayoutState.TabNavigationState snapshot(String modeId, Predicate<String> available) {
        String normalizedModeId = normalize(modeId);
        if (normalizedModeId == null)
            return IDELayoutState.TabNavigationState.empty();

        Cursor cursor = cursors.get(normalizedModeId);
        if (cursor == null)
            return IDELayoutState.TabNavigationState.empty();

        cursor.retain(available);
        if (cursor.entries.isEmpty()) {
            cursors.remove(normalizedModeId);
            return IDELayoutState.TabNavigationState.empty();
        }
        return new IDELayoutState.TabNavigationState(cursor.entries, cursor.index);
    }

    public void restore(String modeId, IDELayoutState.TabNavigationState state) {
        String normalizedModeId = normalize(modeId);
        if (normalizedModeId == null)
            return;

        IDELayoutState.TabNavigationState normalizedState = state == null
            ? IDELayoutState.TabNavigationState.empty()
            : state;
        if (normalizedState.entries().isEmpty()) {
            cursors.remove(normalizedModeId);
            return;
        }
        cursors.put(normalizedModeId, new Cursor(normalizedState.entries(), normalizedState.currentIndex()));
    }

    public void clear() {
        cursors.clear();
    }

    private Optional<String> move(String modeId, int direction, Predicate<String> available) {
        String normalizedModeId = normalize(modeId);
        Cursor cursor = normalizedModeId == null ? null : cursors.get(normalizedModeId);
        if (cursor == null)
            return Optional.empty();

        int candidateIndex = cursor.index + direction;
        while (candidateIndex >= 0 && candidateIndex < cursor.entries.size()) {
            String candidate = cursor.entries.get(candidateIndex);
            if (available.test(candidate)) {
                cursor.index = candidateIndex;
                return Optional.of(candidate);
            }
            candidateIndex += direction;
        }
        return Optional.empty();
    }

    private boolean canMove(String modeId, int direction, Predicate<String> available) {
        String normalizedModeId = normalize(modeId);
        Cursor cursor = normalizedModeId == null ? null : cursors.get(normalizedModeId);
        if (cursor == null)
            return false;

        int candidateIndex = cursor.index + direction;
        while (candidateIndex >= 0 && candidateIndex < cursor.entries.size()) {
            if (available.test(cursor.entries.get(candidateIndex)))
                return true;
            candidateIndex += direction;
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static final class Cursor {
        private final List<String> entries;
        private int index;

        private Cursor() {
            this.entries = new ArrayList<>();
            this.index = -1;
        }

        private Cursor(List<String> entries, int index) {
            this.entries = new ArrayList<>(entries);
            this.index = Math.clamp(index, 0, entries.size() - 1);
        }

        private Optional<String> current() {
            return index < 0 || index >= entries.size() ? Optional.empty() : Optional.of(entries.get(index));
        }

        private void retain(Predicate<String> available) {
            int retainedThroughCurrent = 0;
            var retained = new ArrayList<String>();
            for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                String entry = entries.get(entryIndex);
                if (!available.test(entry))
                    continue;

                retained.add(entry);
                if (entryIndex <= index) {
                    retainedThroughCurrent++;
                }
            }
            entries.clear();
            entries.addAll(retained);
            index = entries.isEmpty() ? -1 : Math.clamp(retainedThroughCurrent - 1, 0, entries.size() - 1);
        }
    }
}
