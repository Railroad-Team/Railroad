package dev.railroadide.railroad.ide.activity;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

/**
 * Reports phases of an activity. Capture the returned counter when dispatching work to other threads.
 */
@FunctionalInterface
public interface ActivityProgress {
    /**
     * Reporter that discards every phase and update.
     */
    ActivityProgress NONE = (phase, unit, initial, total) -> _ -> {
    };

    /**
     * Reporter bound to the current call scope by a tracked operation.
     */
    ScopedValue<ActivityProgress> CURRENT = ScopedValue.newInstance();

    /**
     * Returns the reporter bound to this call scope, or a no-op reporter outside a tracked operation.
     * Separate executor tasks must bind the captured reporter or receive a phase counter explicitly.
     *
     * @return reporter of the current call scope
     */
    static ActivityProgress current() {
        return CURRENT.orElse(NONE);
    }

    /**
     * Starts a fresh phase. The returned sink accepts monotonically increasing completed amounts.
     * A running activity discards updates to phases it has already replaced or closed.
     *
     * @param phase translation key or external phase name
     * @param unit measurement used in the status bar
     * @param initial amount already complete when measurement begins, including resumed downloads
     * @param total known work, or -1 when the total is unavailable
     * @return sink that may be used on another thread
     */
    LongConsumer begin(String phase, Unit unit, long initial, long total);

    /**
     * Starts a countable phase and returns an increment callback safe for concurrent workers.
     *
     * @param phase translation key or external phase name
     * @param unit measurement used in the status bar
     * @param total known work, or -1 when the total is unavailable
     * @return callback that records one more completed unit
     */
    default Runnable count(String phase, Unit unit, long total) {
        if (this == NONE)
            return () -> {
            };

        LongConsumer sink = begin(phase, unit, 0, total);
        var completed = new AtomicLong();
        return () -> sink.accept(completed.incrementAndGet());
    }

    /**
     * Starts a phase whose amount of work is unknown.
     *
     * @param phase translation key or external phase name
     */
    default void indeterminate(String phase) {
        begin(phase, Unit.COUNT, 0, -1);
    }

    /**
     * Steps have known counts but unequal costs, so their count does not drive a time estimate.
     */
    enum Unit {
        /**
         * Countable items, such as files.
         */
        COUNT,
        /**
         * Steps of unequal cost.
         */
        STEPS,
        /**
         * Bytes, shown with binary size units.
         */
        BYTES,
        /**
         * Percentage points from 0 to 100.
         */
        PERCENT
    }
}
