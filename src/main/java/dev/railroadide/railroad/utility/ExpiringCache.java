package dev.railroadide.railroad.utility;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Simple expiring cache for asynchronously resolved values.
 */
public final class ExpiringCache<T> {
    private final Duration ttl;
    private final Object lock = new Object();

    private T cachedValue;
    private Instant fetchedAt;
    private boolean hasValue;
    private CompletableFuture<T> inFlight;

    public ExpiringCache(Duration ttl) {
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative())
            throw new IllegalArgumentException("ttl must not be negative");
    }

    public CompletableFuture<T> getAsync(Supplier<CompletableFuture<T>> valueSupplier) {
        Objects.requireNonNull(valueSupplier, "valueSupplier");
        Instant now = Instant.now();

        synchronized (lock) {
            if (isValueFresh(now))
                return CompletableFuture.completedFuture(cachedValue);

            if (inFlight != null)
                return inFlight;

            CompletableFuture<T> future = Objects.requireNonNull(valueSupplier.get(), "valueSupplier returned null future");
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

    public Optional<T> getIfPresent() {
        synchronized (lock) {
            if (isValueFresh(Instant.now()))
                return Optional.ofNullable(cachedValue);

            return Optional.empty();
        }
    }

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
