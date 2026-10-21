package dev.railroadide.railroad.debug.breakpoint;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BreakpointServiceTest {
    @Test
    public void batchedEditsMoveEachBreakpointAccordingToItsOwnLocation() {
        var service = new BreakpointService();
        Path file = Path.of("Example.java");
        String original = "first\nsecond\nthird\nlast";
        var second = service.add(file, 2);
        var third = service.add(file, 3);
        try (var tracking = service.trackDocument(file, original)) {
            tracking.update(original, "new\nfirst\nsecond\nlast", List.of(
                new BreakpointService.TextChange(0, "", "new\n"),
                new BreakpointService.TextChange(17, "third\n", "")));
            assertEquals(second.id(), service.get(file, 3).orElseThrow().id());
            assertTrue(service.getForFile(file).stream().noneMatch(breakpoint -> breakpoint.id().equals(third.id())));
            tracking.update("");
            assertTrue(service.getForFile(file).isEmpty());
        }
    }

    @Test
    public void movesAreAtomicPreserveIdentityAndIgnoreDuplicateViewReloads() {
        var service = new BreakpointService();
        Path file = Path.of("Example.java");
        String original = "first\nsecond\nthird\nfourth";
        var first = service.add(file, 2);
        var second = service.add(file, 3);
        var events = new ArrayList<BreakpointEvent>();
        try (var firstView = service.trackDocument(file, original);
            var secondView = service.trackDocument(file, original)) {
            service.addListener(event -> {
                assertEquals(first.id(), service.get(file, 4).orElseThrow().id());
                assertEquals(second.id(), service.get(file, 5).orElseThrow().id());
                events.add(event);
            });
            String edited = "inserted\nabove\n" + original;
            firstView.update(edited);
            assertEquals(2, events.size());
            assertTrue(events.stream().allMatch(BreakpointEvent.Changed.class::isInstance));
            secondView.update(edited);
            assertEquals(2, events.size());
            assertEquals(2, service.getForFile(file).size());
        }
    }

    @Test
    public void wholeBufferReplacementsPreserveUnchangedCodeBetweenSeparateEdits() {
        var service = new BreakpointService();
        Path file = Path.of("Example.java");
        String original = "first\nsecond\ntarget\nlast\n";
        var breakpoint = service.add(file, 3);
        try (var tracking = service.trackDocument(file, original)) {
            tracking.update("first\ninserted\nsecond\ntarget\nlast\nappended\n");
            assertEquals(breakpoint.id(), service.get(file, 4).orElseThrow().id());
            tracking.update(original);
            assertEquals(breakpoint, service.get(file, 3).orElseThrow());
        }
    }

    @Test
    public void deletingMarkedLinesRemovesThemAndMovesSurvivorsUp() {
        var service = new BreakpointService();
        Path file = Path.of("Example.java");
        var deleted = service.add(file, 2);
        var survivor = service.add(file, 3);
        var events = new ArrayList<BreakpointEvent>();
        service.addListener(events::add);
        try (var tracking = service.trackDocument(file, "first\ndeleted\nsurvivor")) {
            tracking.update("first\nsurvivor");
            assertEquals(1, service.getForFile(file).size());
            assertEquals(survivor.id(), service.get(file, 2).orElseThrow().id());
            assertTrue(events.contains(new BreakpointEvent.Removed(deleted)));
        }
    }

    @Test
    public void lineJoinsKeepOneBreakpointAndUtf16OffsetsHandleCrLf() {
        var service = new BreakpointService();
        Path file = Path.of("Example.java");
        String original = "first\r\n😀target\r\nlast";
        var first = service.add(file, 1);
        var second = service.add(file, 2);
        try (var tracking = service.trackDocument(file, original)) {
            String inserted = "new\r\n" + original;
            tracking.update(original, inserted, List.of(new BreakpointService.TextChange(0, "", "new\r\n")));
            assertEquals(first.id(), service.get(file, 2).orElseThrow().id());
            assertEquals(second.id(), service.get(file, 3).orElseThrow().id());
            tracking.update(inserted, "new\r\nfirst😀target\r\nlast",
                List.of(new BreakpointService.TextChange(10, "\r\n", "")));
            assertEquals(1, service.getForFile(file).size());
            assertEquals(first.id(), service.get(file, 2).orElseThrow().id());
        }
    }

    @Test
    public void duplicateAddsKeepIdentityAndSnapshotsCannotMutateService() {
        var service = new BreakpointService();
        var events = new ArrayList<BreakpointEvent>();
        service.addListener(events::add);
        Path file = Path.of("Example.java");
        SourceBreakpoint breakpoint = service.add(file, 2);
        assertSame(breakpoint, service.add(file.toAbsolutePath(), 2));
        assertEquals(1, events.size());

        var snapshot = service.getForFile(file);
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        service.toggle(file, 2);
        assertFalse(service.has(file, 2));
        assertEquals(1, snapshot.size());
        assertEquals(new BreakpointEvent.Removed(breakpoint), events.getLast());
        assertThrows(IllegalArgumentException.class, () -> service.add(file, 0));
    }
}
