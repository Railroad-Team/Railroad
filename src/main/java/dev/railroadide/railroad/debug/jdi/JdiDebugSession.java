package dev.railroadide.railroad.debug.jdi;

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;
import dev.railroadide.railroad.debug.DebugSessionListener;
import dev.railroadide.railroad.debug.breakpoint.BreakpointManager;
import dev.railroadide.railroad.debug.breakpoint.SourceBreakpoint;
import dev.railroadide.railroad.debug.model.*;
import dev.railroadide.railroad.debug.source.DebugSource;
import dev.railroadide.railroad.debug.source.SourceResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * Serializes debugger commands and publishes events for one attached virtual machine.
 * Inspection handles are valid only during the suspension that created them; command failures
 * complete their returned futures exceptionally.
 */
public final class JdiDebugSession {
    private final DebugEndpoint endpoint;
    private final SourceResolver sourceResolver;
    private final Collection<SourceBreakpoint> initialBreakpoints;

    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor(factory -> {
        var thread = new Thread(factory, "RailroadDebugCommands");
        thread.setDaemon(true);
        return thread;
    });

    private volatile DebugSessionState state = DebugSessionState.NEW;
    private final List<DebugSessionListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong suspensionGeneration = new AtomicLong();

    private volatile boolean stopRequested;
    private volatile VirtualMachine vm;
    private volatile boolean eventReaderRunning;
    private volatile Thread eventReaderThread;
    private volatile SuspendedContext suspendedContext;

    private BreakpointManager breakpointManager;
    private VariableInspector variableInspector;
    private ExpressionEvaluator expressionEvaluator;

    /**
     * Creates a disconnected session; call {@link #attach()} to connect to the target.
     *
     * @param endpoint socket address of the debug target
     * @param sourceResolver mapping from runtime locations to source content
     * @param initialBreakpoints source breakpoint definitions to install after attachment
     */
    public JdiDebugSession(
        DebugEndpoint endpoint,
        SourceResolver sourceResolver,
        Collection<SourceBreakpoint> initialBreakpoints
    ) {
        this.endpoint = endpoint;
        this.sourceResolver = sourceResolver;
        this.initialBreakpoints = initialBreakpoints;
    }

    /**
     * Connects to the target, installs initial breakpoints and starts the event reader.
     * The no-argument overload retries attachment for up to five seconds.
     *
     * @return future completing after connection setup, or exceptionally on attachment failure
     */
    public CompletableFuture<Void> attach() {
        return attach(Duration.ofSeconds(5), () -> false);
    }

    /**
     * Connects to the target, installs initial breakpoints and starts the event reader.
     * The no-argument overload retries attachment for up to five seconds.
     *
     * @param timeout maximum attachment retry duration
     * @param cancelled supplier returning true when attachment should be cancelled
     * @return future completing after connection setup, or exceptionally on attachment failure
     */
    public CompletableFuture<Void> attach(Duration timeout, BooleanSupplier cancelled) {
        return this.<Void>submit(() -> {
            changeState(DebugSessionState.ATTACHING);
            vm = JdiConnections.attach(endpoint.host(), endpoint.port(), timeout,
                () -> stopRequested || cancelled.getAsBoolean());
            changeState(DebugSessionState.CONFIGURING);

            variableInspector = new VariableInspector();
            expressionEvaluator = new ExpressionEvaluator();
            breakpointManager = new BreakpointManager(
                vm,
                sourceResolver);

            for (SourceBreakpoint breakpoint : initialBreakpoints) {
                breakpointManager.add(breakpoint);
            }

            startEventReader();

            return null;
        }).whenComplete((_, failure) -> {
            if (failure != null) {
                detach();
            }
        });
    }

    /**
     * Resumes the current suspension and invalidates its frame and variable inspection handles.
     *
     * @return future completing when the resume command has been applied
     */
    public CompletableFuture<Void> resume() {
        return submit(() -> {
            requireState(DebugSessionState.SUSPENDED);

            resumeInternal();

            return null;
        });
    }

    /**
     * Suspends the running virtual machine and publishes a pause event with a selected target thread.
     *
     * @return future completing after the suspension has been established
     */
    public CompletableFuture<Void> pause() {
        return submit(() -> {
            requireState(DebugSessionState.RUNNING);
            vm.suspend();

            List<ThreadReference> allThreads = vm.allThreads();
            ThreadReference selectedThread = choosePausedThread(allThreads);

            long generation = suspensionGeneration.incrementAndGet();

            Map<Long, ThreadReference> threads = new LinkedHashMap<>();
            for (ThreadReference thread : allThreads) {
                threads.put(thread.uniqueID(), thread);
            }

            suspendedContext = new SuspendedContext(
                generation,
                null,
                true,
                selectedThread,
                threads);

            variableInspector.beginSuspension(generation);
            changeState(DebugSessionState.SUSPENDED);
            fireEvent(new DebugSessionEvent.Suspended(
                DebugStopReason.PAUSE,
                selectedThread == null ? -1 : selectedThread.uniqueID()));

            return null;
        });
    }

    /**
     * Installs a single line-step request for the suspended thread and resumes execution.
     * The next stop is reported through session events rather than the command future.
     *
     * @param threadId JDI unique identifier of a suspended target thread
     * @param kind method-boundary behavior of the line step
     * @return future completing after the step request is installed and execution resumes
     */
    public CompletableFuture<Void> step(long threadId, DebugStepKind kind) {
        return submit(() -> {
            requireState(DebugSessionState.SUSPENDED);

            ThreadReference thread = requireThread(threadId);
            EventRequestManager requests = vm.eventRequestManager();

            List<StepRequest> existing = requests.stepRequests()
                .stream()
                .filter(req -> req.thread().equals(thread))
                .toList();
            if (!existing.isEmpty()) {
                requests.deleteEventRequests(existing);
            }

            int depth = switch (kind) {
                case INTO -> StepRequest.STEP_INTO;
                case OVER -> StepRequest.STEP_OVER;
                case OUT -> StepRequest.STEP_OUT;
            };

            // STEP_LINE means next different source line
            StepRequest request = requests.createStepRequest(thread, StepRequest.STEP_LINE, depth);
            addStepFilters(request);
            request.addCountFilter(1);
            request.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            request.enable();

            resumeInternal();

            return null;
        });
    }

    /**
     * Snapshots the threads captured for the current suspension.
     *
     * @return future containing target thread identities, names, status codes and suspension flags
     */
    public CompletableFuture<List<DebugThread>> threads() {
        return submit(() -> {
            requireState(DebugSessionState.SUSPENDED);

            return suspendedContext.threads().values().stream()
                .map(thread -> new DebugThread(
                    thread.uniqueID(),
                    thread.name(),
                    thread.status(),
                    thread.isSuspended()))
                .toList();
        });
    }

    /**
     * Reads the suspended thread stack and assigns identifiers scoped to the current suspension.
     *
     * @param threadId JDI unique identifier of a suspended target thread
     * @return future containing frames from the top of the stack downward
     */
    public CompletableFuture<List<DebugFrame>> stackFrames(long threadId) {
        return submit(() -> {
            requireState(DebugSessionState.SUSPENDED);

            ThreadReference thread = requireThread(threadId);
            List<StackFrame> frames = thread.frames();

            List<DebugFrame> debugFrames = new ArrayList<>(frames.size());

            long generation = suspendedContext.generation();
            for (int index = 0; index < frames.size(); index++) {
                StackFrame frame = frames.get(index);
                Location location = frame.location();

                DebugSource source = sourceResolver.resolve(location)
                    .orElse(null);

                String name = location.declaringType().name() + "." + location.method().name();

                debugFrames.add(new DebugFrame(
                    new DebugFrameId(
                        generation,
                        threadId,
                        index),
                    name,
                    source,
                    location.lineNumber()));
            }

            return debugFrames;
        });
    }

    /**
     * Reads the receiver and visible locals of a frame in the current suspension.
     *
     * @param frameId frame identifier from the current suspension
     * @return future containing formatted frame variables
     */
    public CompletableFuture<List<DebugVariable>> variables(DebugFrameId frameId) {
        return submit(() -> {
            StackFrame frame = requireFrame(frameId);
            return variableInspector.inspectFrame(frame);
        });
    }

    /**
     * Reads children of a current suspension-scoped value handle, paging arrays by index.
     *
     * @param reference expansion handle from the current suspension
     * @param start zero-based array offset; ignored for object fields
     * @param count maximum number of array elements; ignored for object fields
     * @return future containing array elements or all visible instance fields
     */
    public CompletableFuture<List<DebugVariable>> expandVariable(long reference, int start, int count) {
        return submit(() -> {
            requireState(DebugSessionState.SUSPENDED);

            return variableInspector.expand(
                reference,
                start,
                count);
        });
    }

    /**
     * Evaluates a watch in a suspended frame without invoking target methods.
     *
     * @param frameId frame identifier from the current suspension
     * @param expression watch expression to resolve in the selected frame
     * @return future containing the formatted result, or failing when the expression cannot be resolved
     */
    public CompletableFuture<DebugVariable> evaluate(DebugFrameId frameId, String expression) {
        return submit(() -> {
            StackFrame frame = requireFrame(frameId);

            Value value = expressionEvaluator.evaluate(
                frame,
                expression);

            return variableInspector.createVariable(
                expression,
                value == null
                    ? "null"
                    : value.type().name(),
                value);
        });
    }

    /**
     * Disconnects the debugger, releases its JDI connection and ends the session without exiting the target.
     *
     * @return future completing after detachment and session cleanup
     */
    public CompletableFuture<Void> detach() {
        stopRequested = true;
        return submitTerminal(() -> {
            if (state == DebugSessionState.TERMINATED)
                return null;
            eventReaderRunning = false;

            if (eventReaderThread != null) {
                eventReaderThread.interrupt();
            }

            if (vm != null) {
                try {
                    vm.dispose();
                } catch (VMDisconnectedException _) {
                }
            }

            finishTerminated();

            return null;
        });
    }

    /**
     * Exits the target virtual machine with status zero and ends the session.
     * An already terminated session is left unchanged.
     *
     * @return future completing after termination and session cleanup
     */
    public CompletableFuture<Void> terminate() {
        stopRequested = true;
        return submitTerminal(() -> {
            if (state == DebugSessionState.TERMINATED)
                return null;

            changeState(DebugSessionState.TERMINATING);

            try {
                if (vm != null) {
                    vm.exit(0);
                }
            } catch (VMDisconnectedException _) {
            }

            finishTerminated();

            return null;
        });
    }

    /**
     * Registers a recipient for events published by the command or event-reader thread.
     *
     * @param listener recipient of debugger events on their publishing thread
     */
    public void addListener(DebugSessionListener listener) {
        listeners.add(listener);
    }

    /**
     * Unregisters a recipient from subsequent session events.
     *
     * @param listener recipient of debugger events on their publishing thread
     */
    public void removeListener(DebugSessionListener listener) {
        listeners.remove(listener);
    }

    /**
     * Queues installation of a source breakpoint in the attached target.
     *
     * @param breakpoint source breakpoint definition to install
     * @return future completing after the definition and runtime requests are installed
     */
    public CompletableFuture<Void> addBreakpoint(SourceBreakpoint breakpoint) {
        return submit(() -> {
            breakpointManager.add(breakpoint);
            return null;
        });
    }

    /**
     * Queues removal of a breakpoint definition and its runtime requests.
     *
     * @param breakpointId stable identifier of the breakpoint to remove
     * @return future completing after removal; unknown identifiers are ignored
     */
    public CompletableFuture<Void> removeBreakpoint(UUID breakpointId) {
        return submit(() -> {
            breakpointManager.remove(breakpointId);
            return null;
        });
    }

    /**
     * Queues removal and reinstallation of the breakpoint with its updated definition.
     *
     * @param breakpoint replacement definition retaining the breakpoint identifier
     * @return future completing after the replacement definition is installed
     */
    public CompletableFuture<Void> updateBreakpoint(SourceBreakpoint breakpoint) {
        return submit(() -> {
            breakpointManager.remove(breakpoint.id());
            breakpointManager.add(breakpoint);
            return null;
        });
    }

    private void handleDisconnect() {
        finishTerminated();
    }

    private void finishTerminated() {
        if (state == DebugSessionState.TERMINATED)
            return;

        suspendedContext = null;

        if (variableInspector != null) {
            variableInspector.endSuspension();
        }

        eventReaderRunning = false;

        if (eventReaderThread != null) {
            eventReaderThread.interrupt();
        }

        try {
            changeState(DebugSessionState.TERMINATED);
            fireEvent(new DebugSessionEvent.Terminated());
        } finally {
            commandExecutor.shutdown();
        }
    }

    private StackFrame requireFrame(DebugFrameId frameId) throws IncompatibleThreadStateException {
        requireState(DebugSessionState.SUSPENDED);
        if (frameId.suspensionGeneration() != suspendedContext.generation())
            throw new IllegalStateException("Stack frame belongs to an old suspension");

        ThreadReference thread = requireThread(frameId.threadId());
        return thread.frame(frameId.frameIndex());
    }

    private void resumeInternal() {
        SuspendedContext context = Objects.requireNonNull(suspendedContext);
        suspendedContext = null;
        variableInspector.endSuspension();

        changeState(DebugSessionState.RUNNING);

        if (context.eventSet() != null) {
            context.eventSet().resume();
        }
        if (context.explicitVmSuspend()) {
            vm.resume();
        }

        fireEvent(new DebugSessionEvent.Resumed());
    }

    // TODO: configurable
    private void addStepFilters(StepRequest request) {
        request.addClassExclusionFilter("java.*");
        request.addClassExclusionFilter("javax.*");
        request.addClassExclusionFilter("jdk.*");
        request.addClassExclusionFilter("sun.*");
        request.addClassExclusionFilter("com.sun.*");
    }

    private ThreadReference requireThread(long threadId) {
        return this.vm.allThreads().stream()
            .filter(thread -> thread != null && thread.uniqueID() == threadId)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Thread " + threadId + " not found"));
    }

    private ThreadReference choosePausedThread(List<ThreadReference> threads) {
        return threads.stream()
            .filter(ThreadReference::isSuspended)
            .findFirst()
            .orElse(null);
    }

    private void requireState(@Nullable DebugSessionState state) {
        if (this.state != state)
            throw new IllegalStateException("Expected debugger state " + state + " but got " + this.state);
    }

    private void startEventReader() {
        eventReaderRunning = true;

        eventReaderThread = new Thread(
            this::pollForEvents,
            "RailroadDebugEvents");
        eventReaderThread.setDaemon(true);
        eventReaderThread.start();
    }

    private void pollForEvents() {
        EventQueue eventQueue = vm.eventQueue();
        while (eventReaderRunning) {
            try {
                EventSet eventSet = eventQueue.remove();
                commandExecutor.execute(() -> handleEventSet(eventSet));
            } catch (InterruptedException _) {
            } catch (VMDisconnectedException _) {
                submit(() -> {
                    handleDisconnect();
                    return null;
                });
                return;
            } catch (RejectedExecutionException _) {
                return;
            }
        }
    }

    private void handleEventSet(EventSet eventSet) {
        if (state == DebugSessionState.TERMINATED)
            return;
        boolean terminate = false;
        ThreadReference stopThread = null;
        DebugStopReason stopReason = null;

        try {
            for (Event event : eventSet) {
                switch (event) {
                    case ClassPrepareEvent prepareEvent -> breakpointManager.onClassPrepared(prepareEvent);
                    case BreakpointEvent breakpointEvent -> {
                        stopThread = breakpointEvent.thread();
                        stopReason = DebugStopReason.BREAKPOINT;
                    }
                    case StepEvent stepEvent -> {
                        cleanupStepEvent(stepEvent);

                        if (stopReason == null) {
                            stopThread = stepEvent.thread();
                            stopReason = DebugStopReason.STEP;
                        }
                    }
                    case VMStartEvent _ -> {
                        if (state == DebugSessionState.CONFIGURING) {
                            changeState(DebugSessionState.RUNNING);
                        }
                    }
                    case VMDeathEvent _,VMDisconnectEvent _ -> terminate = true;
                    case null, default -> {
                    }
                }
            }

            if (terminate) {
                finishTerminated();
                return;
            }

            if (stopThread != null) {
                suspendFromEvent(
                    eventSet,
                    stopThread,
                    stopReason);
            } else {
                eventSet.resume();
                if (state == DebugSessionState.CONFIGURING) {
                    changeState(DebugSessionState.RUNNING);
                }
            }
        } catch (Throwable throwable) {
            fireEvent(new DebugSessionEvent.Error(
                "Debugger event processing failed",
                throwable));

            try {
                eventSet.resume();
            } catch (Throwable _) {
            }
        }
    }

    private void suspendFromEvent(EventSet eventSet, ThreadReference eventThread, DebugStopReason reason) {
        long generation = suspensionGeneration.incrementAndGet();
        Map<Long, ThreadReference> threads = captureThreads(eventThread);
        SuspendedContext previous = suspendedContext;
        boolean explicitVmSuspend = previous != null && previous.explicitVmSuspend();
        if (previous != null && previous.eventSet() != null) {
            previous.eventSet().resume();
        }

        suspendedContext = new SuspendedContext(
            generation,
            eventSet,
            explicitVmSuspend,
            eventThread,
            threads);

        variableInspector.beginSuspension(generation);
        changeState(DebugSessionState.SUSPENDED);
        fireEvent(new DebugSessionEvent.Suspended(
            reason,
            eventThread.uniqueID()));
    }

    private Map<Long, ThreadReference> captureThreads(ThreadReference eventThread) {
        Map<Long, ThreadReference> threads = new LinkedHashMap<>();
        for (ThreadReference thread : vm.allThreads()) {
            threads.put(thread.uniqueID(), thread);
        }

        if (eventThread != null) {
            threads.put(eventThread.uniqueID(), eventThread);
        }

        return threads;
    }

    private void cleanupStepEvent(StepEvent stepEvent) {
        EventRequest request = stepEvent.request();
        if (request != null) {
            try {
                vm.eventRequestManager().deleteEventRequest(request);
            } catch (RuntimeException _) {
            }
        }
    }

    private void changeState(@NotNull DebugSessionState newState) {
        DebugSessionState oldState = state;
        state = newState;
        fireEvent(new DebugSessionEvent.StateChanged(oldState, state));
    }

    private void fireEvent(@NotNull DebugSessionEvent event) {
        for (DebugSessionListener listener : listeners) {
            listener.onDebugEvent(event);
        }
    }

    private <T> CompletableFuture<T> submit(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            commandExecutor.submit(() -> {
                try {
                    future.complete(task.call());
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
        } catch (RejectedExecutionException failure) {
            future.completeExceptionally(failure);
        }
        return future;
    }

    private CompletableFuture<Void> submitTerminal(Callable<Void> task) {
        if (state == DebugSessionState.TERMINATED)
            return CompletableFuture.completedFuture(null);
        return submit(task).exceptionallyCompose(failure -> {
            if (failure instanceof RejectedExecutionException && state == DebugSessionState.TERMINATED)
                return CompletableFuture.completedFuture(null);
            return CompletableFuture.failedFuture(failure);
        });
    }
}
