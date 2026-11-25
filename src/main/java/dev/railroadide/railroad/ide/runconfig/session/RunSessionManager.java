package dev.railroadide.railroad.ide.runconfig.session;

import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.diagnostics.BuildProblems;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tracks a project's run sessions so the console can show them.
 * <p>
 * Starting a configuration again replaces its most recent ended session, which keeps one session per configuration
 * unless several instances run at once. Sessions stay listed until they are replaced or removed. All methods are
 * thread-safe.
 */
public final class RunSessionManager {
    private final BuildProblems buildProblems = new BuildProblems();

    public BuildProblems buildProblems() {
        return buildProblems;
    }

    private final Object lock = new Object();
    private final List<RunSession> sessions = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();

    /**
     * Starts a session for a launch of the configuration.
     *
     * @param configuration the configuration being launched
     * @param debug whether it is launched under the debugger
     * @return the new running session
     */
    public RunSession start(RunConfiguration<?> configuration, boolean debug) {
        Objects.requireNonNull(configuration, "configuration");
        var session = new RunSession(configuration, debug);
        synchronized (lock) {
            RunSession replaced = null;
            for (int index = sessions.size() - 1; index >= 0; index--) {
                RunSession candidate = sessions.get(index);
                if (candidate.configuration().uuid().equals(configuration.uuid()) && candidate.state().isTerminal()) {
                    replaced = sessions.remove(index);
                    break;
                }
            }

            sessions.add(session);
            for (Listener listener : listeners) {
                listener.sessionStarted(session, replaced);
            }
        }

        return session;
    }

    /**
     * Removes an ended session. Running sessions stay listed.
     *
     * @param session the session to remove
     * @return whether the session was removed
     */
    public boolean remove(RunSession session) {
        Objects.requireNonNull(session, "session");
        synchronized (lock) {
            if (!session.state().isTerminal() || !sessions.remove(session))
                return false;

            for (Listener listener : listeners) {
                listener.sessionRemoved(session);
            }

            return true;
        }
    }

    /**
     * Returns the listed sessions.
     *
     * @return the sessions, oldest first
     */
    public List<RunSession> sessions() {
        synchronized (lock) {
            return List.copyOf(sessions);
        }
    }

    /**
     * Reports the listed sessions as started, then notifies the listener of later changes.
     *
     * @param listener the listener to notify
     * @return an action that stops further notifications
     */
    public Runnable subscribe(Listener listener) {
        Objects.requireNonNull(listener, "listener");
        synchronized (lock) {
            for (RunSession session : sessions) {
                listener.sessionStarted(session, null);
            }

            listeners.add(listener);
        }

        return () -> {
            synchronized (lock) {
                listeners.remove(listener);
            }
        };
    }

    /**
     * Receives session list changes. Callbacks run while the manager's lock is held, on the thread that changed the
     * list; implementations must return quickly and must not call back into the manager.
     */
    public interface Listener {
        /**
         * Called when a session is started.
         *
         * @param session the new session
         * @param replaced the ended session of the same configuration it replaces, or {@code null}
         */
        void sessionStarted(RunSession session, @Nullable RunSession replaced);

        /**
         * Called when an ended session is removed.
         *
         * @param session the removed session
         */
        void sessionRemoved(RunSession session);
    }
}
