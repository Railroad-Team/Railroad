package dev.railroadide.railroad.java.cli;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link ProcessExecution} utility class, specifically focusing on its timeout
 * and interruption handling during process execution.
 */
class ProcessExecutionTest {

    @Test
    void enforceTimeoutReturnsWhenProcessCompletesWithinLimit() {
        var process = new StubProcess(true, false, true);
        assertDoesNotThrow(() -> ProcessExecution.enforceTimeout(process, 1, TimeUnit.SECONDS, "java"));
        assertTrue(process.isWaitForCalled(), "waitFor should be invoked when enforcing timeouts");
        assertFalse(process.isDestroyCalled(), "completed processes should not be destroyed");
    }

    @Test
    void enforceTimeoutDestroysHungProcess() {
        var process = new StubProcess(false, false, false);
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> ProcessExecution.enforceTimeout(process, 1, TimeUnit.SECONDS, "jarsigner"));

        assertTrue(exception.getMessage().contains("timed out"));
        assertTrue(process.isDestroyCalled(), "hung process should be destroyed");
        assertTrue(process.isDestroyForciblyCalled(), "hung process should be forcefully destroyed");
    }

    @Test
    void enforceTimeoutHandlesInterruptions() {
        var process = new StubProcess(true, true, true);
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> ProcessExecution.enforceTimeout(process, 1, TimeUnit.SECONDS, "keytool"));

        assertTrue(exception.getMessage().contains("Interrupted while waiting"));
        assertTrue(process.isDestroyCalled(), "interrupted wait should still destroy the process");
        assertTrue(Thread.currentThread().isInterrupted(), "interrupt status should be restored");
        Thread.interrupted(); // Clear for other tests
    }

    @Test
    void enforceTimeoutSkipsWaitWhenDurationIsNonPositive() {
        var process = new StubProcess(true, false, true);
        assertDoesNotThrow(() -> ProcessExecution.enforceTimeout(process, 0, TimeUnit.SECONDS, "jar"));
        assertFalse(process.isWaitForCalled(), "no wait should occur when timeout is disabled");
    }

    private static final class StubProcess extends Process {
        private final boolean waitResult;
        private final boolean interruptDuringWait;
        private final boolean destroyStopsProcess;
        private boolean waitForCalled;
        private boolean destroyCalled;
        private boolean destroyForciblyCalled;
        private boolean alive = true;

        private StubProcess(boolean waitResult, boolean interruptDuringWait, boolean destroyStopsProcess) {
            this.waitResult = waitResult;
            this.interruptDuringWait = interruptDuringWait;
            this.destroyStopsProcess = destroyStopsProcess;
        }

        boolean isWaitForCalled() {
            return waitForCalled;
        }

        boolean isDestroyCalled() {
            return destroyCalled;
        }

        boolean isDestroyForciblyCalled() {
            return destroyForciblyCalled;
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            waitForCalled = true;
            alive = false;
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            waitForCalled = true;
            if (interruptDuringWait)
                throw new InterruptedException("simulated interrupt");

            if (waitResult) {
                alive = false;
            }

            return waitResult;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            destroyCalled = true;
            if (destroyStopsProcess) {
                alive = false;
            }
        }

        @Override
        public Process destroyForcibly() {
            destroyForciblyCalled = true;
            alive = false;
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public long pid() {
            return 0;
        }

        @Override
        public ProcessHandle.Info info() {
            return ProcessHandle.current().info();
        }

        @Override
        public Stream<ProcessHandle> children() {
            return Stream.empty();
        }

        @Override
        public Stream<ProcessHandle> descendants() {
            return Stream.empty();
        }

        @Override
        public boolean supportsNormalTermination() {
            return true;
        }
    }
}
