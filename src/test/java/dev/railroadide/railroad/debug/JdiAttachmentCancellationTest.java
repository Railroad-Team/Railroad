package dev.railroadide.railroad.debug;

import dev.railroadide.railroad.debug.breakpoint.BreakpointService;
import dev.railroadide.railroad.debug.model.DebugEndpoint;
import dev.railroadide.railroad.debug.model.DebugSessionEvent;
import dev.railroadide.railroad.debug.model.DebugSessionState;
import dev.railroadide.railroad.debug.source.DependencySourceIndex;
import dev.railroadide.railroad.debug.source.SourceResolver;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class JdiAttachmentCancellationTest {
    @Test
    public void stopInterruptsLongAttachmentAndAllowsAnotherSession() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        var service = new DebugService();
        for (int attempt = 0; attempt < 2; attempt++) {
            var attaching = new CountDownLatch(1);
            var pending = service.startSession(new DebugEndpoint("127.0.0.1", port),
                new SourceResolver(List.of(), DependencySourceIndex.EMPTY), new BreakpointService(), _ -> true,
                event -> {
                    if (event instanceof DebugSessionEvent.StateChanged changed
                        && changed.newState() == DebugSessionState.ATTACHING) {
                        attaching.countDown();
                    }
                }, Duration.ofMinutes(1), () -> false);
            assertTrue(attaching.await(3, TimeUnit.SECONDS));
            service.terminateActiveSession().get(3, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class, () -> pending.get(3, TimeUnit.SECONDS));
            assertTrue(service.getActiveSession().isEmpty());
        }
    }
}
