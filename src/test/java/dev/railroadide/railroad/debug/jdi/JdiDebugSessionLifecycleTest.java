package dev.railroadide.railroad.debug.jdi;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.EventSet;
import dev.railroadide.railroad.debug.model.*;
import dev.railroadide.railroad.debug.source.DependencySourceIndex;
import dev.railroadide.railroad.debug.source.SourceResolver;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class JdiDebugSessionLifecycleTest {
    @Test
    public void queuedBreakpointPreservesExplicitPauseUntilResume() throws Exception {
        var explicitSuspensions = new AtomicInteger();
        var eventSuspensions = new AtomicInteger(1);
        ThreadReference thread = proxy(ThreadReference.class, (_, method, _) -> switch (method.getName()) {
            case "uniqueID" -> 1L;
            case "isSuspended" -> true;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        VirtualMachine vm = proxy(VirtualMachine.class, (_, method, _) -> switch (method.getName()) {
            case "suspend" -> {
                explicitSuspensions.incrementAndGet();
                yield null;
            }
            case "resume" -> {
                explicitSuspensions.decrementAndGet();
                yield null;
            }
            case "allThreads" -> List.of(thread);
            case "dispose" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        EventSet eventSet = proxy(EventSet.class, (_, method, _) -> {
            if (method.getName().equals("resume")) {
                eventSuspensions.decrementAndGet();
                return null;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        var session = session();
        set(session, "vm", vm);
        set(session, "state", DebugSessionState.RUNNING);
        set(session, "variableInspector", new VariableInspector());
        try {
            session.pause().get(3, TimeUnit.SECONDS);
            var stop = JdiDebugSession.class.getDeclaredMethod("suspendFromEvent", EventSet.class,
                ThreadReference.class, DebugStopReason.class);
            stop.setAccessible(true);
            stop.invoke(session, eventSet, thread, DebugStopReason.BREAKPOINT);
            session.resume().get(3, TimeUnit.SECONDS);
            assertEquals(0, explicitSuspensions.get());
            assertEquals(0, eventSuspensions.get());
        } finally {
            session.detach().get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    public void failedAttachmentAlsoReleasesCommandExecutor() throws Exception {
        var session = session();
        assertThrows(ExecutionException.class,
            () -> session.attach(Duration.ofSeconds(5), () -> true).get(3, TimeUnit.SECONDS));
        var field = JdiDebugSession.class.getDeclaredField("commandExecutor");
        field.setAccessible(true);
        var executor = (ExecutorService) field.get(session);
        assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        session.terminate().get(3, TimeUnit.SECONDS);
    }

    @Test
    public void terminalCleanupShutsDownExecutorAndStopRemainsIdempotent() throws Exception {
        var session = session();
        session.detach().get(3, TimeUnit.SECONDS);
        var field = JdiDebugSession.class.getDeclaredField("commandExecutor");
        field.setAccessible(true);
        var executor = (ExecutorService) field.get(session);
        assertTrue(executor.isShutdown());
        assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        session.detach().get(3, TimeUnit.SECONDS);
        session.terminate().get(3, TimeUnit.SECONDS);
        assertTrue(session.threads().isCompletedExceptionally());
    }

    private static JdiDebugSession session() {
        return new JdiDebugSession(new DebugEndpoint("127.0.0.1", 5005),
            new SourceResolver(List.of(), DependencySourceIndex.EMPTY), List.of());
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
