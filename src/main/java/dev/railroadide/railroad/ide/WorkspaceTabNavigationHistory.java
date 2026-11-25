package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentVersion;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Maintains bounded back and forward editor histories independently for each workspace mode. Entries identify a
 * tab and, for explicit navigation such as Go to Definition, the caret position in the exact document revision
 * that was visible. Positions are transient and are not persisted with the layout.
 */
public final class WorkspaceTabNavigationHistory {
    /**
     * Maximum number of tab visits retained for each mode.
     */
    public static final int MAX_ENTRIES_PER_MODE = 100;

    private final Map<String, Cursor> cursors = new LinkedHashMap<>();

    /**
     * A caret offset in one document revision. Consumers must not apply it to a different revision.
     *
     * @param documentId document containing the offset
     * @param version revision in which the offset was recorded
     * @param offset zero-based UTF-16 caret offset
     */
    public record Position(DocumentId documentId, DocumentVersion version, int offset) {
        /**
         * Creates a revision-bound caret position.
         *
         * @param documentId document containing the offset
         * @param version revision in which the offset was recorded
         * @param offset zero-based UTF-16 caret offset
         */
        public Position {
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(version, "version");
            if (offset < 0)
                throw new IllegalArgumentException("Offset must be non-negative");
        }
    }

    /**
     * A history destination: a tab and, when recorded by explicit navigation, a caret position within it.
     *
     * @param tabId tab identifier
     * @param position caret position, or {@code null} for a plain tab visit
     */
    public record Destination(String tabId, @Nullable Position position) {
    }

    /**
     * Records a tab visit, discarding forward history and bounding the history size.
     *
     * @param modeId workspace mode identifier
     * @param tabId visited tab identifier
     */
    public void visit(String modeId, String tabId) {
        visit(modeId, tabId, null);
    }

    /**
     * Records a visit to a position within a tab. A visit to the current tab only replaces its position when the
     * current entry has none; a different position in the same tab becomes a new entry so that it can be
     * returned to. Forward history is discarded whenever an entry is added.
     *
     * @param modeId workspace mode identifier
     * @param tabId visited tab identifier
     * @param position caret position within the tab, or {@code null} for a plain tab visit
     */
    public void visit(String modeId, String tabId, @Nullable Position position) {
        String normalizedModeId = normalize(modeId);
        String normalizedTabId = normalize(tabId);
        if (normalizedModeId == null || normalizedTabId == null)
            return;

        Cursor cursor = cursors.computeIfAbsent(normalizedModeId, _ -> new Cursor());
        Entry current = cursor.current().orElse(null);
        if (current != null && current.tabId.equals(normalizedTabId)) {
            if (position == null || position.equals(current.position))
                return;

            if (current.position == null) {
                current.position = position;
                return;
            }
        }

        if (cursor.index + 1 < cursor.entries.size()) {
            cursor.entries.subList(cursor.index + 1, cursor.entries.size()).clear();
        }

        cursor.entries.add(new Entry(normalizedTabId, position));
        cursor.index = cursor.entries.size() - 1;
        while (cursor.entries.size() > MAX_ENTRIES_PER_MODE) {
            cursor.entries.removeFirst();
            cursor.index--;
        }
    }

    /**
     * Replaces the position of the current entry when it belongs to the supplied tab, so that returning to it
     * restores the latest caret rather than an earlier one. Other entries and forward history are unchanged.
     *
     * @param modeId workspace mode identifier
     * @param tabId tab expected to be current
     * @param position caret position within the tab
     * @return whether the current entry belonged to the tab and was updated
     */
    public boolean updateCurrent(String modeId, String tabId, Position position) {
        Objects.requireNonNull(position, "position");
        String normalizedModeId = normalize(modeId);
        String normalizedTabId = normalize(tabId);
        Cursor cursor = normalizedModeId == null ? null : cursors.get(normalizedModeId);
        Entry current = cursor == null ? null : cursor.current().orElse(null);
        if (current == null || !current.tabId.equals(normalizedTabId))
            return false;

        current.position = position;
        return true;
    }

    /**
     * Moves to the nearest earlier available tab.
     *
     * @param modeId workspace mode identifier
     * @param available predicate identifying tabs that still exist
     * @return tab identifier, or empty if no earlier available tab exists
     */
    public Optional<String> back(String modeId, Predicate<String> available) {
        return backDestination(modeId, available).map(Destination::tabId);
    }

    /**
     * Moves to the nearest later available tab.
     *
     * @param modeId workspace mode identifier
     * @param available predicate identifying tabs that still exist
     * @return tab identifier, or empty if no later available tab exists
     */
    public Optional<String> forward(String modeId, Predicate<String> available) {
        return forwardDestination(modeId, available).map(Destination::tabId);
    }

    /**
     * Moves to the nearest earlier available entry, including its recorded position.
     *
     * @param modeId workspace mode identifier
     * @param available predicate identifying tabs that still exist
     * @return destination, or empty if no earlier available entry exists
     */
    public Optional<Destination> backDestination(String modeId, Predicate<String> available) {
        return move(modeId, -1, available);
    }

    /**
     * Moves to the nearest later available entry, including its recorded position.
     *
     * @param modeId workspace mode identifier
     * @param available predicate identifying tabs that still exist
     * @return destination, or empty if no later available entry exists
     */
    public Optional<Destination> forwardDestination(String modeId, Predicate<String> available) {
        return move(modeId, 1, available);
    }

    /**
     * Checks for an earlier available tab without moving the cursor.
     *
     * @param modeId workspace mode identifier
     * @param available predicate identifying tabs that still exist
     * @return whether backward navigation is possible
     */
    public boolean canGoBack(String modeId, Predicate<String> available) {
        return canMove(modeId, -1, available);
    }

    /**
     * Checks for a later available tab without moving the cursor.
     *
     * @param modeId workspace mode identifier
     * @param available predicate identifying tabs that still exist
     * @return whether forward navigation is possible
     */
    public boolean canGoForward(String modeId, Predicate<String> available) {
        return canMove(modeId, 1, available);
    }

    /**
     * Removes unavailable entries and captures the remaining history for a mode. Positions are not captured.
     *
     * @param modeId workspace mode identifier
     * @param available predicate identifying tabs that still exist
     * @return normalized history snapshot
     */
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

        return new IDELayoutState.TabNavigationState(
            cursor.entries.stream().map(entry -> entry.tabId).toList(),
            cursor.index);
    }

    /**
     * Replaces a mode's tab history from saved state.
     *
     * @param modeId workspace mode identifier
     * @param state history to restore, or null to clear it
     */
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

    /**
     * Removes the tab histories for all workspace modes.
     */
    public void clear() {
        cursors.clear();
    }

    private Optional<Destination> move(String modeId, int direction, Predicate<String> available) {
        String normalizedModeId = normalize(modeId);
        Cursor cursor = normalizedModeId == null ? null : cursors.get(normalizedModeId);
        if (cursor == null)
            return Optional.empty();

        int candidateIndex = cursor.index + direction;
        while (candidateIndex >= 0 && candidateIndex < cursor.entries.size()) {
            Entry candidate = cursor.entries.get(candidateIndex);
            if (available.test(candidate.tabId)) {
                cursor.index = candidateIndex;
                return Optional.of(new Destination(candidate.tabId, candidate.position));
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
            if (available.test(cursor.entries.get(candidateIndex).tabId))
                return true;

            candidateIndex += direction;
        }

        return false;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static final class Entry {
        private final String tabId;
        private @Nullable Position position;

        private Entry(String tabId, @Nullable Position position) {
            this.tabId = tabId;
            this.position = position;
        }
    }

    private static final class Cursor {
        private final List<Entry> entries;
        private int index;

        private Cursor() {
            this.entries = new ArrayList<>();
            this.index = -1;
        }

        private Cursor(List<String> entries, int index) {
            this.entries = new ArrayList<>(entries.stream().map(tabId -> new Entry(tabId, null)).toList());
            this.index = Math.clamp(index, 0, entries.size() - 1);
        }

        private Optional<Entry> current() {
            return index < 0 || index >= entries.size() ? Optional.empty() : Optional.of(entries.get(index));
        }

        private void retain(Predicate<String> available) {
            int retainedThroughCurrent = 0;
            List<Entry> retained = new ArrayList<Entry>();
            for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                Entry entry = entries.get(entryIndex);
                if (!available.test(entry.tabId))
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
