package dev.railroadide.railroad.ide.sst.provider.api;

import dev.railroadide.railroad.ide.sst.request.api.*;
import dev.railroadide.railroad.utility.ShutdownHooks;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Bounded execution boundary for language providers. The caller's scheduler owns priority and queueing;
 * this service admits provider code without an additional queue and bounds the time callers wait.
 * Each invocation receives a separate virtual thread, with global and per-provider admission limits.
 * A provider ignoring cancellation retains its allowance until it actually exits, so repeated timeouts
 * cannot create unlimited abandoned workers. Other providers retain independent admission allowances.
 *
 * <p>
 * Cancellation and timeout interrupt the worker and discard its result. Java cannot forcibly stop
 * arbitrary plugin code; providers must cooperate, and external side effects cannot be rolled back.
 * Callbacks must collect results privately and publish only after this boundary returns successfully.
 * The publication owner must still validate current document/workspace versions and supersession.
 * Instances are thread-safe, and closing cancels outstanding calls without waiting for plugin code.
 */
public final class ProviderExecutor implements AutoCloseable {
    private static final long POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(10);

    private static final class Shared {
        private static final ProviderExecutor INSTANCE = new ProviderExecutor(64, 8, 128, Duration.ofMillis(250));
        static {
            ShutdownHooks.addHook(INSTANCE::close);
        }
    }

    /**
     * Returns the shared provider execution boundary for the application.
     *
     * @return the application-owned executor, closed by application shutdown
     */
    public static ProviderExecutor shared() {
        return Shared.INSTANCE;
    }

    private final int maximumActive;
    private final int maximumPerProvider;
    private final int maximumProviders;
    private final long slowNanos;
    private final Map<String, Measurements> providers = new LinkedHashMap<>(16, 1, true);
    private final Map<Thread, CompletableFuture<?>> running = new HashMap<>();
    private volatile boolean closed;
    private int active;

    /**
     * Creates an isolated boundary with explicit resource and diagnostic limits.
     * Inactive least-recently-used aggregates are evicted at the provider limit; active providers
     * are never evicted. If all retained providers are active, new identities are rejected.
     *
     * @param maximumActive maximum concurrent provider executions
     * @param maximumPerProvider maximum concurrent executions of one provider
     * @param maximumProviders maximum retained provider aggregates
     * @param slowThreshold duration after which an execution is reported as slow
     */
    public ProviderExecutor(int maximumActive, int maximumPerProvider, int maximumProviders, Duration slowThreshold) {
        if (maximumActive <= 0 || maximumPerProvider <= 0 || maximumProviders <= 0)
            throw new IllegalArgumentException("Provider limits must be positive");
        Objects.requireNonNull(slowThreshold, "slowThreshold");
        if (slowThreshold.isNegative())
            throw new IllegalArgumentException("Slow threshold must not be negative");
        this.maximumActive = maximumActive;
        this.maximumPerProvider = maximumPerProvider;
        this.maximumProviders = maximumProviders;
        this.slowNanos = slowThreshold.toNanos();
    }

    /**
     * Executes a callback while preserving its captured request identity. The callback should poll
     * the supplied request and interruption. For a provider-specific cooperative deadline inside
     * the callback, use {@link #executeWithContext}; callers retaining the original context should
     * construct it with the same or a shorter deadline.
     *
     * @param providerId stable identity, independent of document/version
     * @param context captured workspace and cancellation controls
     * @param budget maximum caller wait, capped by the request deadline
     * @param task callback producing an unpublished result
     * @param <T> result type
     * @return result after activity and deadline checks
     */
    public <T> T execute(String providerId, RequestContext context, Duration budget, Callable<T> task) {
        Objects.requireNonNull(task, "task");
        return executeInternal(providerId, context, budget, _ -> task.call());
    }

    /**
     * Executes a callback with child controls bounded by both the caller and provider budgets.
     * The child retains the exact captured workspace and, for a feature request, document snapshot.
     * Its token additionally observes this invocation's cancellation; it is a distinct request instance.
     *
     * @param providerId stable identity, independent of document/version
     * @param context captured parent request
     * @param budget maximum provider budget, including dispatch delay
     * @param task callback receiving the child request
     * @param <T> result type
     * @return unpublished result after checking parent and child activity
     */
    public <T> T executeWithContext(
        String providerId,
        RequestContext context,
        Duration budget,
        Function<RequestContext, T> task
    ) {
        Objects.requireNonNull(task, "task");
        return executeInternal(providerId, context, budget, task::apply);
    }

    private <T> T executeInternal(String providerId, RequestContext parent, Duration budget, Operation<T> task) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(parent, "context");
        Objects.requireNonNull(budget, "budget");
        if (providerId.isBlank())
            throw new IllegalArgumentException("Provider identity must not be blank");
        if (budget.isNegative())
            throw new IllegalArgumentException("Provider budget must not be negative");
        parent.checkActive();
        long queuedAt = System.nanoTime();
        Deadline deadline = Deadline.after(budget);
        var cancelled = new AtomicBoolean();
        CancellationToken token = new CancellationToken() {
            @Override
            public boolean isCancellationRequested() {
                return closed || cancelled.get() || parent.cancellationToken().isCancellationRequested();
            }

            @Override
            public void throwIfCancellationRequested() {
                parent.checkActive();
                CancellationToken.super.throwIfCancellationRequested();
            }
        };
        RequestContext child = parent instanceof FeatureRequestContext feature
            ? new FeatureRequestContext(parent.workspaceSnapshot(), feature.documentSnapshot(), token, deadline)
            : new WorkspaceRequestContext(parent.workspaceSnapshot(), token, deadline);
        var result = new CompletableFuture<T>();
        Measurements measurements;
        Thread worker;
        synchronized (this) {
            if (closed)
                throw new RejectedExecutionException("Provider executor is closed");
            measurements = measurements(providerId);
            if (active >= maximumActive || measurements.active >= maximumPerProvider) {
                measurements.rejected++;
                throw new RejectedExecutionException("Provider execution limit reached: " + providerId);
            }
            active++;
            measurements.active++;
            measurements.awaiting++;
            measurements.lastProjectId = parent.workspaceSnapshot().projectId();
            measurements.lastWorkspaceRevision = parent.workspaceSnapshot().revision();
            worker = Thread.ofVirtual().name("railroad-provider-" + providerId).unstarted(() -> {
                long started = System.nanoTime();
                synchronized (this) {
                    measurements.totalQueueNanos += started - queuedAt;
                }
                T value = null;
                Throwable failure = null;
                try {
                    parent.checkActive();
                    child.checkActive();
                    value = task.run(child);
                    parent.checkActive();
                    child.checkActive();
                } catch (Throwable exception) {
                    failure = exception;
                } finally {
                    long elapsed = System.nanoTime() - started;
                    synchronized (this) {
                        measurements.totalExecutionNanos += elapsed;
                        measurements.maximumExecutionNanos = Math.max(measurements.maximumExecutionNanos, elapsed);
                        if (elapsed >= slowNanos) {
                            measurements.slow++;
                        }
                        measurements.active--;
                        active--;
                        running.remove(Thread.currentThread());
                    }
                }
                if (failure == null) {
                    result.complete(value);
                } else {
                    result.completeExceptionally(failure);
                }
            });
            running.put(worker, result);
            try {
                worker.start();
            } catch (RuntimeException | Error failure) {
                running.remove(worker);
                measurements.active--;
                measurements.awaiting--;
                active--;
                recordFailure(measurements, failure);
                throw failure;
            }
        }
        boolean completedNormally = false;
        try {
            while (true) {
                parent.checkActive();
                child.checkActive();
                try {
                    long wait = Math.min(POLL_NANOS, child.deadline().remaining().orElseThrow().toNanos());
                    T value = result.get(wait, TimeUnit.NANOSECONDS);
                    parent.checkActive();
                    child.checkActive();
                    synchronized (this) {
                        if (closed)
                            throw new CancellationException("Provider executor closed");
                        measurements.succeeded++;
                    }
                    completedNormally = true;
                    return value;
                } catch (TimeoutException _) {
                    // Recheck both controls before waiting again.
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            synchronized (this) {
                measurements.cancelled++;
            }
            throw new CancellationException("Interrupted while awaiting provider " + providerId);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof CancellationException cancellation) {
                cause = cancellationOutcome(parent, child, cancellation);
            }
            recordFailure(measurements, cause);
            if (cause instanceof RuntimeException runtime)
                throw runtime;
            throw new CompletionException(cause);
        } catch (CancellationException exception) {
            CancellationException cause = cancellationOutcome(parent, child, exception);
            recordFailure(measurements, cause);
            throw cause;
        } finally {
            if (!completedNormally) {
                cancelled.set(true);
                worker.interrupt();
            }
            synchronized (this) {
                measurements.awaiting--;
            }
        }
    }

    private static CancellationException cancellationOutcome(
        RequestContext parent,
        RequestContext child,
        CancellationException failure
    ) {
        try {
            parent.checkActive();
            child.checkActive();
        } catch (CancellationException activity) {
            return activity;
        }
        CancellationException cause = failure;
        while (cause.getCause() instanceof CancellationException nested) {
            cause = nested;
        }
        return cause;
    }

    private synchronized Measurements measurements(String providerId) {
        Measurements existing = providers.get(providerId);
        if (existing != null)
            return existing;
        if (providers.size() == maximumProviders) {
            var iterator = providers.values().iterator();
            while (iterator.hasNext()) {
                Measurements candidate = iterator.next();
                if (candidate.active == 0 && candidate.awaiting == 0) {
                    iterator.remove();
                    break;
                }
            }
            if (providers.size() == maximumProviders)
                throw new RejectedExecutionException("Provider identity limit reached");
        }
        var created = new Measurements(providerId);
        providers.put(providerId, created);
        return created;
    }

    private synchronized void recordFailure(Measurements measurements, Throwable failure) {
        if (failure instanceof DeadlineExceededException) {
            measurements.timedOut++;
        } else if (failure instanceof CancellationException) {
            measurements.cancelled++;
        } else {
            measurements.failed++;
            measurements.lastFailureType = failure.getClass().getName();
        }
    }

    /**
     * Records a failure isolated by a provider host that continues with other rules. Cancellation
     * remains an expected outcome and is counted separately. Full exceptions are never retained.
     * If diagnostics are closed or all identity slots are active, this observation is dropped.
     *
     * @param providerId identity used for provider execution
     * @param failure isolated failure
     */
    public synchronized void reportFailure(String providerId, Throwable failure) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(failure, "failure");
        if (providerId.isBlank())
            throw new IllegalArgumentException("Provider identity must not be blank");
        if (closed)
            return;
        try {
            recordFailure(measurements(providerId), failure);
        } catch (RejectedExecutionException _) {
            // Diagnostics must not turn an isolated rule failure into a request failure.
        }
    }

    /**
     * Takes a snapshot of execution measurements for each retained provider.
     *
     * @return immutable provider aggregates in provider-identity order
     */
    public synchronized List<ProviderHealth> health() {
        return providers.values().stream().map(Measurements::snapshot)
            .sorted(Comparator.comparing(ProviderHealth::providerId)).toList();
    }

    /**
     * Returns provider measurement snapshots that contain execution warnings, ordered by provider identity.
     *
     * @return immutable slow, timed-out, failed, or rejected provider diagnostics
     */
    public List<ProviderHealth> diagnostics() {
        return health().stream().filter(ProviderHealth::hasWarnings).toList();
    }

    /** Cancels active calls and releases retained aggregates without waiting for arbitrary plugin code. */
    @Override
    public synchronized void close() {
        if (closed)
            return;
        closed = true;
        running.forEach((worker, result) -> {
            result.completeExceptionally(new CancellationException("Provider executor closed"));
            worker.interrupt();
        });
        providers.clear();
    }

    @FunctionalInterface
    private interface Operation<T> {
        T run(RequestContext context) throws Exception;
    }

    private static final class Measurements {
        private final String providerId;
        private long succeeded;
        private long failed;
        private long cancelled;
        private long timedOut;
        private long rejected;
        private long slow;
        private int active;
        private int awaiting;
        private long totalQueueNanos;
        private long totalExecutionNanos;
        private long maximumExecutionNanos;
        private String lastFailureType = "";
        private String lastProjectId = "";
        private long lastWorkspaceRevision;

        private Measurements(String providerId) {
            this.providerId = providerId;
        }

        private ProviderHealth snapshot() {
            return new ProviderHealth(providerId, succeeded, failed, cancelled, timedOut, rejected, slow, active,
                totalQueueNanos, totalExecutionNanos, maximumExecutionNanos, lastFailureType,
                lastProjectId, lastWorkspaceRevision);
        }
    }
}
