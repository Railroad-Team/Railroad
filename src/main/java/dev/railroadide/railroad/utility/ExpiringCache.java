package dev.railroadide.railroad.utility;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Simple expiring cache for asynchronously resolved values.
 *
 * @param <T> the type of the cached value
 */
public final class ExpiringCache<T> {
    private final Duration ttl;
    private final Object lock = new Object();

    private T cachedValue;
    private Instant fetchedAt;
    private boolean hasValue;
    private CompletableFuture<T> inFlight;

    /**
     * Creates a new expiring cache with the specified time-to-live (TTL).
     *
     * @param ttl the time-to-live for cached values; must not be negative
     * @throws NullPointerException     if ttl is null
     * @throws IllegalArgumentException if ttl is negative
     */
    public ExpiringCache(Duration ttl) {
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative())
            throw new IllegalArgumentException("ttl must not be negative");
    }

    /**
     * Returns a CompletableFuture that will complete with the cached value if it is still valid,
     * or will invoke the provided valueSupplier to fetch a new value if the cached value has expired.
     *
     * @param valueSupplier a supplier that returns a CompletableFuture for fetching a new value
     * @return a CompletableFuture that will complete with the cached or newly fetched value
     * @throws NullPointerException if valueSupplier is null or returns null
     */
    public CompletableFuture<T> getAsync(Supplier<CompletableFuture<T>> valueSupplier) {
        Objects.requireNonNull(valueSupplier, "valueSupplier");
        Instant now = Instant.now();

        synchronized (lock) {
            if (isValueFresh(now))
                return CompletableFuture.completedFuture(cachedValue);

            if (inFlight != null)
                return inFlight;

            CompletableFuture<T> future = Objects.requireNonNull(valueSupplier.get(),
                "valueSupplier returned null future");
            inFlight = future;

            future.whenComplete((value, throwable) -> {
                synchronized (lock) {
                    if (inFlight == future) {
                        if (throwable == null) {
                            cachedValue = value;
                            fetchedAt = Instant.now();
                            hasValue = true;
                        } else {
                            cachedValue = null;
                            fetchedAt = null;
                            hasValue = false;
                        }

                        inFlight = null;
                    }
                }
            });

            return future;
        }
    }

    /**
     * Returns an Optional containing the cached value if it is still valid, or an empty Optional if the cached value has expired.
     *
     * @return an Optional containing the cached value if it is still valid, or an empty Optional if the cached value has expired
     */
    public Optional<T> getIfPresent() {
        synchronized (lock) {
            if (isValueFresh(Instant.now()))
                return Optional.ofNullable(cachedValue);

            return Optional.empty();
        }
    }

    /**
     * Invalidates the cached value, causing the next call to getAsync to fetch a new value.
     */
    public void invalidate() {
        synchronized (lock) {
            cachedValue = null;
            fetchedAt = null;
            hasValue = false;
            inFlight = null;
        }
    }

    private boolean isValueFresh(Instant now) {
        if (!hasValue || fetchedAt == null)
            return false;

        if (ttl.isZero())
            return false;

        return fetchedAt.plus(ttl).isAfter(now);
    }
}
