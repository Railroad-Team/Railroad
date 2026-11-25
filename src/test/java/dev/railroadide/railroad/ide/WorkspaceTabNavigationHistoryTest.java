package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentVersion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class WorkspaceTabNavigationHistoryTest {
    private static final String MODE = "code";
    private final DocumentId first = DocumentId.create();
    private final DocumentId second = DocumentId.create();

    @Test
    public void plainTabVisitsDoNotDuplicateTheCurrentEntry() {
        var history = new WorkspaceTabNavigationHistory();
        history.visit(MODE, "a");
        history.visit(MODE, "a");
        history.visit(MODE, "b");
        assertEquals(List.of("a", "b"), history.snapshot(MODE, _ -> true).entries());
    }

    @Test
    public void navigationOriginAndDestinationArePositionedEntries() {
        var history = new WorkspaceTabNavigationHistory();
        history.visit(MODE, "a");
        assertTrue(history.updateCurrent(MODE, "a", position(first, 3)));
        history.visit(MODE, "b");
        history.visit(MODE, "b", position(second, 7));

        assertEquals(destination("a", position(first, 3)), history.backDestination(MODE, _ -> true));
        assertEquals(destination("b", position(second, 7)), history.forwardDestination(MODE, _ -> true));
    }

    @Test
    public void aSecondPositionInTheSameTabBecomesItsOwnEntry() {
        var history = new WorkspaceTabNavigationHistory();
        history.visit(MODE, "a", position(first, 3));
        history.visit(MODE, "a", position(first, 20));
        history.visit(MODE, "a", position(first, 20));
        history.visit(MODE, "a");

        assertEquals(destination("a", position(first, 3)), history.backDestination(MODE, _ -> true));
        assertFalse(history.canGoBack(MODE, _ -> true));
    }

    @Test
    public void updatingAnotherTabDoesNotRewriteOrTruncateHistory() {
        var history = new WorkspaceTabNavigationHistory();
        history.visit(MODE, "a", position(first, 1));
        history.visit(MODE, "b", position(second, 2));
        history.backDestination(MODE, _ -> true);
        assertFalse(history.updateCurrent(MODE, "b", position(second, 5)));
        assertTrue(history.canGoForward(MODE, _ -> true));
        assertEquals(destination("b", position(second, 2)), history.forwardDestination(MODE, _ -> true));
    }

    @Test
    public void newVisitsDiscardForwardHistory() {
        var history = new WorkspaceTabNavigationHistory();
        history.visit(MODE, "a", position(first, 1));
        history.visit(MODE, "b", position(second, 2));
        history.backDestination(MODE, _ -> true);
        history.visit(MODE, "c");
        assertFalse(history.canGoForward(MODE, _ -> true));
    }

    @Test
    public void unavailableTabsAreSkippedAndPositionsAreNotPersisted() {
        var history = new WorkspaceTabNavigationHistory();
        history.visit(MODE, "a", position(first, 1));
        history.visit(MODE, "closed", position(second, 2));
        history.visit(MODE, "c", position(second, 3));

        assertEquals(destination("a", position(first, 1)),
            history.backDestination(MODE, tab -> !tab.equals("closed")));

        var restored = new WorkspaceTabNavigationHistory();
        restored.restore(MODE, history.snapshot(MODE, _ -> true));
        assertEquals(Optional.of(new WorkspaceTabNavigationHistory.Destination("closed", null)),
            restored.forwardDestination(MODE, _ -> true));
    }

    @Test
    public void historyIsBoundedPerMode() {
        var history = new WorkspaceTabNavigationHistory();
        for (int index = 0; index <= WorkspaceTabNavigationHistory.MAX_ENTRIES_PER_MODE; index++) {
            history.visit(MODE, "tab", position(first, index));
        }

        assertEquals(WorkspaceTabNavigationHistory.MAX_ENTRIES_PER_MODE,
            history.snapshot(MODE, _ -> true).entries().size());
        assertTrue(history.snapshot("git", _ -> true).entries().isEmpty());
    }

    @Test
    public void positionsRejectNegativeOffsets() {
        assertThrows(IllegalArgumentException.class, () -> position(first, -1));
    }

    private static WorkspaceTabNavigationHistory.Position position(DocumentId id, int offset) {
        return new WorkspaceTabNavigationHistory.Position(id, DocumentVersion.initial(), offset);
    }

    private static Optional<WorkspaceTabNavigationHistory.Destination> destination(
        String tab,
        WorkspaceTabNavigationHistory.Position position
    ) {
        return Optional.of(new WorkspaceTabNavigationHistory.Destination(tab, position));
    }
}
