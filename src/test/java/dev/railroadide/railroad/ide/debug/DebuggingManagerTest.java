package dev.railroadide.railroad.ide.debug;

import dev.railroadide.railroad.debug.jdi.JdiDebugSession;
import dev.railroadide.railroad.debug.model.*;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class DebuggingManagerTest {
    @BeforeAll
    public static void startJavaFx() throws Exception {
        var started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void resumeAndTerminationClearInspectionButRetainWatches() throws Exception {
        onFxThread(() -> {
            for (DebugSessionEvent event : new DebugSessionEvent[]{
                new DebugSessionEvent.StateChanged(DebugSessionState.SUSPENDED, DebugSessionState.RUNNING),
                new DebugSessionEvent.Terminated()
            }) {
                var manager = new DebuggingManager(null);
                var pending = new CompletableFuture<JdiDebugSession>();
                set(manager, "connection", pending);
                manager.addWatch("count");
                manager.activeProperty().set(true);
                manager.pausedProperty().set(true);
                var thread = new DebugThread(1, "main", 1, true);
                var frame = new DebugFrame(new DebugFrameId(1, 1, 0), "Main.main", null, 5);
                list(manager, "threads").add(thread);
                list(manager, "frames").add(frame);
                list(manager, "variables").add(new DebugVariable("count", "int", "12", 0, 0));
                property(manager, "selectedThread").set(thread);
                property(manager, "selectedFrame").set(frame);
                assertTrue(manager.canStep());
                accept(manager, pending, event);
                assertFalse(manager.isPaused());
                assertFalse(manager.canStep());
                assertTrue(manager.getThreads().isEmpty());
                assertTrue(manager.getFrames().isEmpty());
                assertTrue(manager.getVariables().isEmpty());
                assertNull(manager.selectedThreadProperty().get());
                assertNull(manager.selectedFrameProperty().get());
                assertEquals("count", manager.getWatchResults().getFirst().watch().expression());
                assertNull(manager.getWatchResults().getFirst().value());
                assertTrue(manager.expandVariable(1, 0, 100).isCompletedExceptionally());
                assertThrows(UnsupportedOperationException.class, () -> manager.getThreads().add(thread));
            }
        });
    }

    @Test
    public void oldSessionCannotClearNewSessionAndUnsuspendedThreadCannotStep() throws Exception {
        onFxThread(() -> {
            var manager = new DebuggingManager(null);
            var current = new CompletableFuture<JdiDebugSession>();
            set(manager, "connection", current);
            manager.activeProperty().set(true);
            manager.pausedProperty().set(true);
            property(manager, "selectedThread").set(new DebugThread(2, "worker", 1, false));
            assertFalse(manager.canStep());
            accept(manager, new CompletableFuture<>(), new DebugSessionEvent.Terminated());
            assertTrue(manager.isActive());
            assertTrue(manager.isPaused());
            manager.addWatch("  count  ");
            manager.addWatch(" ");
            assertEquals(1, manager.getWatchResults().size());
            var watch = manager.getWatchResults().getFirst().watch();
            assertEquals("count", watch.expression());
            manager.removeWatch(watch);
            assertTrue(manager.getWatchResults().isEmpty());
        });
    }

    @Test
    public void debuggerErrorsExposeMessageAndCauseOnlyForCurrentSession() throws Exception {
        onFxThread(() -> {
            var manager = new DebuggingManager(null);
            var pending = new CompletableFuture<JdiDebugSession>();
            set(manager, "connection", pending);
            var error = new DebugSessionEvent.Error("Debugger event processing failed",
                new IllegalStateException("Connection lost"));
            accept(manager, new CompletableFuture<>(), error);
            assertEquals("", manager.inspectionErrorProperty().get());
            accept(manager, pending, error);
            assertEquals("Debugger event processing failed: Connection lost", manager.inspectionErrorProperty().get());
            accept(manager, pending, new DebugSessionEvent.Error("Unavailable", null));
            assertEquals("Unavailable", manager.inspectionErrorProperty().get());
        });
    }

    @Test
    public void objectReferencesAreExpandableWithoutIndexedChildren() {
        assertTrue(new DebugVariable("player", "Player", "Player@53", 7, 0).hasChildren());
        assertFalse(new DebugVariable("count", "int", "12", 0, 0).hasChildren());
    }

    private static void accept(
        DebuggingManager manager,
        CompletableFuture<JdiDebugSession> connection,
        DebugSessionEvent event
    ) throws Exception {
        var method = DebuggingManager.class.getDeclaredMethod("acceptEvent", CompletableFuture.class,
            DebugSessionEvent.class);
        method.setAccessible(true);
        method.invoke(manager, connection, event);
    }

    private static void set(DebuggingManager manager, String name, Object value) throws Exception {
        var field = DebuggingManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    @SuppressWarnings("unchecked")
    private static ObservableList<Object> list(DebuggingManager manager, String name) throws Exception {
        var field = DebuggingManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return (ObservableList<Object>) field.get(manager);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProperty<Object> property(DebuggingManager manager, String name) throws Exception {
        var field = DebuggingManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return (ObjectProperty<Object>) field.get(manager);
    }

    private static void onFxThread(ThrowingRunnable action) throws Exception {
        var task = new FutureTask<Void>(() -> {
            action.run();
            return null;
        });
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
