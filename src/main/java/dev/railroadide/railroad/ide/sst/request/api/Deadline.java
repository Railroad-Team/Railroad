package dev.railroadide.railroad.ide.sst.request.api;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * A fixed time budget for cooperative SST work, measured with a monotonic nanosecond counter.
 * A finite deadline captures its starting counter and budget when created; querying it
 * does not restart or extend that budget. The exact budget boundary counts as expired.
 * {@link #none()} represents an unlimited budget, independently of any finite duration.
 * <p>
 * Counter readings follow {@link System#nanoTime()} semantics: their origin is arbitrary,
 * readings may be negative, and the counter may wrap. Finite deadlines support elapsed
 * intervals from creation that fit in a nonnegative signed {@code long} nanosecond count.
 * Only differences between readings from the same time source describe elapsed time.
 * <p>
 * Configuration is fixed after construction. Instances support concurrent queries when
 * their time source supports concurrent reads and obeys the monotonic counter contract.
 * Queries only report timing; they do not cancel requests, interrupt threads, or schedule
 * background work. Deadlines hold no external resources and require no disposal.
 */
public final class Deadline {
    private static final Deadline UNLIMITED = new Deadline(System::nanoTime, Long.MAX_VALUE, true);

    /**
     * Returns the shared deadline representing an unlimited budget.
     * It never expires, and {@link #remaining()} returns an empty optional.
     *
     * @return the shared deadline with no time limit
     */
    public static Deadline none() {
        return UNLIMITED;
    }

    /**
     * Starts a finite budget using {@link System#nanoTime()}.
     * A zero duration is valid and immediately expired.
     *
     * @param timeout nonnegative duration representable as a signed {@code long} nanosecond count
     * @return a deadline whose budget starts during this call
     * @throws NullPointerException if {@code timeout} is {@code null}
     * @throws IllegalArgumentException if {@code timeout} is negative
     * @throws ArithmeticException if a nonnegative timeout cannot be represented in nanoseconds
     */
    public static Deadline after(Duration timeout) {
        return after(timeout, System::nanoTime);
    }

    /**
     * Starts a finite budget using a caller-supplied monotonic nanosecond counter.
     * The starting counter is captured once. The supplier must follow
     * {@link System#nanoTime()} elapsed-time semantics and support concurrent reads if
     * this deadline will be shared between threads. A controlled supplier allows callers
     * to verify time-dependent behaviour without sleeping.
     *
     * @param timeout nonnegative duration representable as a signed {@code long} nanosecond count
     * @param nanoTimeSource time source used for both creation and subsequent queries
     * @return a deadline whose budget starts during this call
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if {@code timeout} is negative
     * @throws ArithmeticException if a nonnegative timeout cannot be represented in nanoseconds
     */
    public static Deadline after(Duration timeout, LongSupplier nanoTimeSource) {
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(nanoTimeSource, "nanoTimeSource");
        if (timeout.isNegative())
            throw new IllegalArgumentException("timeout must be non-negative");

        return new Deadline(nanoTimeSource, timeout.toNanos());
    }

    private final LongSupplier nanoTimeSource;
    private final long startNanos;
    private final long budgetNanos;
    private final boolean isUnlimited;

    private Deadline(LongSupplier nanoTimeSource, long budgetNanos, boolean isUnlimited) {
        Objects.requireNonNull(nanoTimeSource, "nanoTimeSource");
        if (budgetNanos < 0)
            throw new IllegalArgumentException("budgetNanos must be non-negative");

        this.startNanos = nanoTimeSource.getAsLong();
        this.budgetNanos = budgetNanos;
        this.nanoTimeSource = nanoTimeSource;
        this.isUnlimited = isUnlimited;
    }

    private Deadline(LongSupplier nanoTimeSource, long deadlineNanos) {
        this(nanoTimeSource, deadlineNanos, false);
    }

    /**
     * Reports whether the finite budget is exhausted at the sampled counter reading.
     * An unlimited deadline never expires. A {@code false} result does not prevent
     * expiration immediately after this check.
     *
     * @return {@code true} when elapsed time is at least the finite budget, otherwise {@code false}
     */
    public boolean isExpired() {
        if (isUnlimited)
            return false;

        long elapsedNanos = nanoTimeSource.getAsLong() - startNanos;
        return elapsedNanos >= budgetNanos;
    }

    /**
     * Reports the remaining budget at one sampled counter reading.
     * An exhausted finite deadline returns a present {@link Duration#ZERO}; an empty
     * optional is reserved for an unlimited deadline. Separate calls may observe
     * different readings as time advances.
     *
     * @return the nonnegative remaining finite duration, or empty when there is no deadline
     */
    public Optional<Duration> remaining() {
        if (isUnlimited)
            return Optional.empty();

        long remainingNanos = startNanos + budgetNanos - nanoTimeSource.getAsLong();
        return remainingNanos <= 0 ? Optional.of(Duration.ZERO) : Optional.of(Duration.ofNanos(remainingNanos));
    }
}
