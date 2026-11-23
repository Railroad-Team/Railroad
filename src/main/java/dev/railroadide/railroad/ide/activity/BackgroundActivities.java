package dev.railroadide.railroad.ide.activity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

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
 * All methods are thread-safe. Listeners are notified on the thread that started, updated or finished an activity, and
 * notifications from different threads may arrive in any order, so listeners read {@link #running()} again.
 */
public final class BackgroundActivities {
    private static final BackgroundActivities SHARED = new BackgroundActivities();

    private final LongSupplier nanoTime;
    private final Map<Long, Handle> running = new LinkedHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private long nextId;

    public BackgroundActivities() {
        this(System::nanoTime);
    }

    /** Creates a registry with a monotonic nanosecond clock for estimating phase durations. */
    public BackgroundActivities(LongSupplier nanoTime) {
        this.nanoTime = Objects.requireNonNull(nanoTime);
    }

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
        Handle handle;
        synchronized (this) {
            handle = new Handle(++nextId, Objects.requireNonNull(titleKey));
            running.put(handle.id, handle);
        }
        notifyListeners();
        return handle;
    }

    /**
     * Returns the running activities, oldest first.
     *
     * @return immutable snapshot of running activities
     */
    public synchronized List<Activity> running() {
        long now = nanoTime.getAsLong();
        return running.values().stream().map(handle -> handle.phase.snapshot(now)).toList();
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

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }

    /**
     * One piece of running work.
     *
     * @param phase translation key or external phase name, empty when unknown
     * @param unit unit of the completed and total values
     * @param completed completed work in this phase
     * @param total expected work, or -1 when unknown
     * @param remainingSeconds estimated seconds left in this phase, or -1 when unavailable
     * @param id identifier unique within the registry
     * @param titleKey translation key describing the work
     */
    public record Activity(
        long id,
        String titleKey,
        String phase,
        ActivityProgress.Unit unit,
        long completed,
        long total,
        long remainingSeconds
    ) {
        public double fraction() {
            if (total == 0)
                return 1;
            return total > 0 ? (double) completed / total : -1;
        }
    }

    /** Stops notifications when closed. */
    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * Owns the progress and lifetime of one activity.
     */
    public final class Handle implements ActivityProgress, AutoCloseable {
        private final long id;
        private final String titleKey;
        private Phase phase;

        private Handle(long id, String titleKey) {
            this.id = id;
            this.titleKey = titleKey;
            this.phase = new Phase("", Unit.COUNT, 0, -1);
        }

        @Override
        public LongConsumer begin(String label, Unit unit, long initial, long total) {
            Objects.requireNonNull(label);
            Objects.requireNonNull(unit);
            if (initial < 0 || total < -1 || total >= 0 && initial > total)
                throw new IllegalArgumentException("Invalid progress range");
            Phase next;
            synchronized (BackgroundActivities.this) {
                if (phase == null)
                    return _ -> {
                    };
                next = new Phase(label, unit, initial, total);
                phase = next;
            }
            notifyListeners();
            return next;
        }

        @Override
        public void close() {
            synchronized (BackgroundActivities.this) {
                if (phase == null)
                    return;
                phase = null;
                running.remove(id);
            }
            notifyListeners();
        }

        private final class Phase implements LongConsumer {
            private final String label;
            private final Unit unit;
            private final long initial;
            private final long total;
            private final long started = nanoTime.getAsLong();
            private long completed;
            private int samples;
            private long sampledAt;
            private long estimate = -1;

            private Phase(String label, Unit unit, long initial, long total) {
                this.label = label;
                this.unit = unit;
                this.initial = initial;
                this.completed = initial;
                this.total = total;
            }

            @Override
            public void accept(long value) {
                synchronized (BackgroundActivities.this) {
                    long done = Math.min(value, total);
                    if (phase != this || done <= completed)
                        return;
                    completed = done;
                    samples++;
                    sampledAt = nanoTime.getAsLong();
                    long elapsed = sampledAt - started;
                    estimate = -1;
                    if (unit != Unit.STEPS && samples >= 2 && done < total && elapsed >= TimeUnit.SECONDS.toNanos(1)) {
                        double seconds = (double) elapsed / TimeUnit.SECONDS.toNanos(1);
                        estimate = (long) Math.ceil(seconds * (total - done) / (done - initial));
                    }
                }
                notifyListeners();
            }

            private Activity snapshot(long now) {
                long remaining = estimate > 0 ? estimate - TimeUnit.NANOSECONDS.toSeconds(now - sampledAt) : -1;
                return new Activity(id, titleKey, label, unit, completed, total, remaining > 0 ? remaining : -1);
            }
        }
    }
}
