package dev.railroadide.railroad.ide.activity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks long-running background work, such as indexing or a build import, so the IDE can show what it is doing.
 * Work registers an activity for as long as it runs:
 *
 * <pre>{@code
 * try (var _ = BackgroundActivities.shared().start("railroad.activity.indexing_project")) {
 *     indexProject();
 * }
 * }</pre>
 *
 * All methods are thread-safe. Listeners are notified on the thread that started or finished an activity, and
 * notifications from different threads may arrive in any order, so listeners read {@link #running()} again.
 */
public final class BackgroundActivities {
    private static final BackgroundActivities SHARED = new BackgroundActivities();

    private final AtomicLong ids = new AtomicLong();
    private final Map<Long, Activity> running = new LinkedHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /**
     * Returns the application-wide activity registry.
     *
     * @return the shared registry
     */
    public static BackgroundActivities shared() {
        return SHARED;
    }

    /**
     * Registers running work until the returned handle is closed.
     *
     * @param titleKey translation key describing the work
     * @return handle that ends the activity when closed; closing it again has no effect
     */
    public Handle start(String titleKey) {
        var activity = new Activity(ids.incrementAndGet(), Objects.requireNonNull(titleKey, "titleKey"));
        synchronized (this) {
            running.put(activity.id(), activity);
        }
        notifyListeners();
        return new Handle(activity);
    }

    /**
     * Returns the running activities, oldest first.
     *
     * @return immutable snapshot of running activities
     */
    public synchronized List<Activity> running() {
        return List.copyOf(running.values());
    }

    /**
     * Notifies a listener after every change until the subscription is closed.
     *
     * @param listener callback that reads {@link #running()}
     * @return subscription that stops notifications when closed
     */
    public Subscription observe(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private void finish(Activity activity) {
        synchronized (this) {
            running.remove(activity.id());
        }
        notifyListeners();
    }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }

    /**
     * One piece of running work.
     *
     * @param id identifier unique within the registry
     * @param titleKey translation key describing the work
     */
    public record Activity(long id, String titleKey) {
    }

    /** Stops notifications when closed. */
    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    /** Ends its activity when closed. */
    public final class Handle implements AutoCloseable {
        private final Activity activity;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Handle(Activity activity) {
            this.activity = activity;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                finish(activity);
            }
        }
    }
}
