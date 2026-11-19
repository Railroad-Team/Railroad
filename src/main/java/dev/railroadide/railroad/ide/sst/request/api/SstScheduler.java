package dev.railroadide.railroad.ide.sst.request.api;

import dev.railroadide.railroad.utility.ShutdownHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * Bounded shared scheduling for text and binary language requests. Each editor or
 * project owns a {@link Scope}; scopes own cancellation, while this service owns
 * workers. Closing one scope never shuts down workers used by another owner.
 *
 * <p>
 * Queued tasks run in priority order and FIFO order within a priority. With more
 * than one worker, one worker is reserved for completion and signature help so that
 * slow background work cannot occupy every dispatch slot. Saturation
 * rejects new work without running it on the caller. Cancellation removes queued
 * work and interrupts running work, but providers must still cooperate. Provider
 * execution deadlines and isolation belong to the provider execution boundary.
 * Callers must validate captured workspace/document revisions immediately before
 * publishing a result, including after dispatching publication to another thread.
 * A worker commits its outcome under the scheduler lock before delivering completion
 * callbacks. Scope cancellation and supersession cannot revoke an already committed
 * outcome, even while its callbacks are still running.
 */
public final class SstScheduler implements AutoCloseable {
    private static final class Shared {
        private static final SstScheduler INSTANCE = create();

        private static SstScheduler create() {
            var scheduler = new SstScheduler(Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 2, 8), 512);
            ShutdownHooks.addHook(scheduler::close);
            return scheduler;
        }
    }

    private final Object lock = new Object();
    private final PriorityQueue<Task<?>> queue = new PriorityQueue<>();
    private final Set<Scope> scopes = new HashSet<>();
    private final List<Thread> workers = new ArrayList<>();
    private final int queueCapacity;
    private long sequence;
    private boolean closed;

    /**
     * Returns the application-owned scheduler, disposed by the application shutdown hooks.
     * Individual consumers must close their scopes, not this shared scheduler.
     *
     * @return application scheduler
     */
    public static SstScheduler shared() {
        return Shared.INSTANCE;
    }

    /**
     * Creates an independently owned scheduler with fixed concurrency and bounded waiting work.
     *
     * @param parallelism number of daemon workers
     * @param queueCapacity maximum queued tasks, excluding running tasks
     */
    public SstScheduler(int parallelism, int queueCapacity) {
        if (parallelism <= 0 || queueCapacity <= 0)
            throw new IllegalArgumentException("parallelism and queueCapacity must be positive");
        this.queueCapacity = queueCapacity;
        for (int index = 0; index < parallelism; index++) {
            boolean interactiveOnly = parallelism > 1 && index == 0;
            workers.add(Thread.ofPlatform().daemon().name("railroad-sst-worker-" + index)
                .start(() -> work(interactiveOnly)));
        }
    }

    /**
     * Opens a cancellation scope for one editor, project, or other bounded lifecycle.
     *
     * @return independently closable request owner
     * @throws RejectedExecutionException if this scheduler has closed
     */
    public Scope newScope() {
        synchronized (lock) {
            if (closed)
                throw new RejectedExecutionException("SST scheduler is closed");
            var scope = new Scope();
            scopes.add(scope);
            return scope;
        }
    }

    /**
     * Reports whether the caller is already executing on this scheduler. Synchronous
     * batch operations can execute nested work inline to avoid waiting on their own workers.
     *
     * @return whether the current thread is one of this scheduler's workers
     */
    public boolean isWorkerThread() {
        return workers.contains(Thread.currentThread());
    }

    private void work(boolean interactiveOnly) {
        while (true) {
            Task<?> task;
            synchronized (lock) {
                while (!closed && (queue.isEmpty() || interactiveOnly
                    && queue.peek().priority.compareTo(RequestPriority.SIGNATURE_HELP) > 0)) {
                    try {
                        lock.wait();
                    } catch (InterruptedException _) {
                        if (closed)
                            return;
                    }
                }
                if (closed)
                    return;
                task = queue.remove();
                task.runner = Thread.currentThread();
                task.started = true;
            }
            task.run();
            Thread.interrupted();
        }
    }

    /** Cancels all owned requests and stops dispatch without waiting for uncooperative work. */
    @Override
    public void close() {
        List<Task<?>> cancelled = new ArrayList<>();
        synchronized (lock) {
            if (closed)
                return;
            closed = true;
            for (Scope scope : scopes) {
                scope.closed = true;
                cancelled.addAll(scope.tasks.values());
                scope.tasks.clear();
            }
            scopes.clear();
            cancelled.forEach(this::cancelLocked);
            lock.notifyAll();
        }
        cancelled.forEach(this::completeCancellation);
        workers.forEach(Thread::interrupt);
    }

    private void cancelLocked(Task<?> task) {
        if (task.outcomeCommitted)
            return;
        task.cancelled = true;
        task.cancellation.cancel();
        queue.remove(task);
        if (task.runner != null) {
            task.runner.interrupt();
        }
    }

    private void completeCancellation(Task<?> task) {
        boolean queued;
        synchronized (lock) {
            if (!task.cancelled)
                return;
            queued = !task.started;
        }
        task.result.cancel(false);
        if (queued) {
            task.finished.complete(null);
        }
    }

    /**
     * Owns requests for one lifecycle. Keys are local to this scope, allowing multiple
     * editors and projects to use the same feature names independently. Every method
     * supports concurrent callers. Closing releases all captured request references.
     */
    public final class Scope implements AutoCloseable {
        private final Map<Object, Task<?>> tasks = new HashMap<>();
        private boolean closed;

        private Scope() {
        }

        /**
         * Schedules work and cancels any prior request with the same key. The supplied
         * source must be the cancellation owner of the supplied context; the scheduler
         * retains and signals it on supersession, future cancellation, and closure.
         * Context identity is preserved for {@link FeatureResult} correlation.
         *
         * @param <T> result type
         * @param key feature or operation identity within this scope
         * @param priority queued dispatch priority
         * @param context captured text, binary, or workspace request
         * @param cancellation source observed by the context
         * @param operation provider operation, invoked only on a worker
         * @return completion, exceptional if cancelled, expired, failed, or rejected
         */
        public <T> CompletableFuture<T> submit(
            Object key,
            RequestPriority priority,
            RequestContext context,
            CancellationTokenSource cancellation,
            Supplier<T> operation
        ) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(priority, "priority");
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(cancellation, "cancellation");
            Objects.requireNonNull(operation, "operation");
            if (context.cancellationToken() != cancellation.token())
                throw new IllegalArgumentException("The request must observe the supplied cancellation source");
            Task<?> previous;
            Task<T> task;
            synchronized (lock) {
                if (closed || SstScheduler.this.closed)
                    return CompletableFuture
                        .failedFuture(new RejectedExecutionException("SST request owner is closed"));
                previous = tasks.remove(key);
                if (previous != null) {
                    cancelLocked(previous);
                }
                task = new Task<>(this, key, priority, context, cancellation, operation, sequence++);
                if (queue.size() < queueCapacity) {
                    tasks.put(key, task);
                    queue.add(task);
                    lock.notifyAll();
                } else {
                    task.result.completeExceptionally(new RejectedExecutionException("SST request queue is full"));
                }
            }
            if (previous != null) {
                completeCancellation(previous);
            }
            task.result.whenComplete((_, _) -> {
                if (task.result.isCancelled()) {
                    boolean queued;
                    synchronized (lock) {
                        tasks.remove(key, task);
                        cancelLocked(task);
                        queued = !task.started;
                    }
                    if (queued) {
                        task.finished.complete(null);
                    }
                }
            });
            return task.result;
        }

        /**
         * Cancels queued or running work for a key, including during a UI debounce interval.
         *
         * @param key operation identity within this scope
         */
        public void cancel(Object key) {
            Task<?> task;
            synchronized (lock) {
                task = tasks.remove(key);
                if (task != null) {
                    cancelLocked(task);
                }
            }
            if (task != null) {
                completeCancellation(task);
            }
        }

        /**
         * Captures completion of requests currently owned by this scope, including
         * their synchronous completion callbacks. Later submissions are excluded.
         * Cancellation is a completed outcome; this barrier does not propagate failures.
         *
         * @return completion barrier for the currently owned work
         */
        public CompletableFuture<Void> completion() {
            synchronized (lock) {
                return CompletableFuture.allOf(tasks.values().stream().map(task -> task.finished)
                    .toArray(CompletableFuture[]::new));
            }
        }

        /** Cancels all requests owned by this scope and rejects future submissions. */
        @Override
        public void close() {
            List<Task<?>> cancelled;
            synchronized (lock) {
                if (closed)
                    return;
                closed = true;
                cancelled = List.copyOf(tasks.values());
                tasks.clear();
                scopes.remove(this);
                cancelled.forEach(SstScheduler.this::cancelLocked);
            }
            cancelled.forEach(SstScheduler.this::completeCancellation);
        }
    }

    private final class Task<T> implements Comparable<Task<?>> {
        private final Scope scope;
        private final Object key;
        private final RequestPriority priority;
        private final RequestContext context;
        private final CancellationTokenSource cancellation;
        private final Supplier<T> operation;
        private final long sequence;
        private final CompletableFuture<T> result = new CompletableFuture<>();
        private final CompletableFuture<Void> finished = new CompletableFuture<>();
        private boolean cancelled;
        private boolean started;
        private boolean outcomeCommitted;
        private Thread runner;

        private Task(
            Scope scope,
            Object key,
            RequestPriority priority,
            RequestContext context,
            CancellationTokenSource cancellation,
            Supplier<T> operation,
            long sequence
        ) {
            this.scope = scope;
            this.key = key;
            this.priority = priority;
            this.context = context;
            this.cancellation = cancellation;
            this.operation = operation;
            this.sequence = sequence;
        }

        @Override
        public int compareTo(Task<?> other) {
            int rank = priority.compareTo(other.priority);
            return rank == 0 ? Long.compare(sequence, other.sequence) : rank;
        }

        private void run() {
            T value = null;
            Throwable failure = null;
            try {
                context.checkActive();
                value = operation.get();
                context.checkActive();
            } catch (Throwable exception) {
                failure = exception;
            }
            synchronized (lock) {
                runner = null;
                if (cancelled || scope.tasks.get(key) != this) {
                    failure = new CancellationException("SST request was superseded");
                }
                outcomeCommitted = true;
            }
            if (failure == null) {
                result.complete(value);
            } else {
                result.completeExceptionally(failure);
            }
            synchronized (lock) {
                scope.tasks.remove(key, this);
            }
            finished.complete(null);
        }
    }
}
