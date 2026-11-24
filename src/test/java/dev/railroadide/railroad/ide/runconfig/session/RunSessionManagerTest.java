package dev.railroadide.railroad.ide.runconfig.session;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RunSessionManagerTest {
    @Test
    public void rerunReplacesEndedSessionOfSameConfiguration() {
        var manager = new RunSessionManager();
        var events = new ArrayList<String>();
        var configuration = RunSessionTest.configuration();
        RunSession first = manager.start(configuration, false);
        manager.subscribe(new RecordingListener(events, first));

        first.finish(0, null);
        RunSession second = manager.start(configuration, true);

        assertEquals(List.of(second), manager.sessions());
        assertTrue(second.isDebug());
        assertEquals(List.of("started first replacing none", "started other replacing first"), events);
    }

    @Test
    public void concurrentInstancesAreKeptSideBySide() {
        var manager = new RunSessionManager();
        var configuration = RunSessionTest.configuration();
        RunSession first = manager.start(configuration, false);
        RunSession second = manager.start(configuration, false);

        assertEquals(List.of(first, second), manager.sessions());
    }

    @Test
    public void otherConfigurationsAreNotReplaced() {
        var manager = new RunSessionManager();
        RunSession first = manager.start(RunSessionTest.configuration(), false);
        first.finish(0, null);
        RunSession second = manager.start(RunSessionTest.configuration(), false);

        assertEquals(List.of(first, second), manager.sessions());
    }

    @Test
    public void onlyEndedSessionsCanBeRemoved() {
        var manager = new RunSessionManager();
        var events = new ArrayList<String>();
        RunSession session = manager.start(RunSessionTest.configuration(), false);
        manager.subscribe(new RecordingListener(events, session));

        assertFalse(manager.remove(session));
        session.finish(null, null);
        assertTrue(manager.remove(session));
        assertFalse(manager.remove(session));

        assertEquals(List.of(), manager.sessions());
        assertEquals(List.of("started first replacing none", "removed first"), events);
    }

    @Test
    public void unsubscribedListenerStopsReceiving() {
        var manager = new RunSessionManager();
        var events = new ArrayList<String>();
        Runnable unsubscribe = manager.subscribe(new RecordingListener(events, null));
        unsubscribe.run();
        manager.start(RunSessionTest.configuration(), false);

        assertEquals(List.of(), events);
    }

    private record RecordingListener(List<String> events, @Nullable RunSession first)
        implements
            RunSessionManager.Listener {
        @Override
        public void sessionStarted(RunSession session, @Nullable RunSession replaced) {
            events.add("started " + name(session) + " replacing " + (replaced == null ? "none" : name(replaced)));
        }

        @Override
        public void sessionRemoved(RunSession session) {
            events.add("removed " + name(session));
        }

        private String name(RunSession session) {
            return session == first ? "first" : "other";
        }
    }
}
