package dev.railroadide.railroad.project.creation.service;

import dev.railroadide.core.project.creation.service.GradleService;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

@EqualsAndHashCode
@ToString
@AllArgsConstructor
public final class ToolingGradleService implements GradleService {
    private OutputStream logStream;

    @Override
    public void runTasks(Path projectDir, String... tasks) throws RuntimeException, IOException {
        Objects.requireNonNull(projectDir, "projectDir");
        Objects.requireNonNull(tasks, "tasks");

        int attempt = 0;
        while (true) {
            attempt++;
            GradleConnector connector = GradleConnector.newConnector()
                .forProjectDirectory(projectDir.toFile())
                .useBuildDistribution(); // uses gradle.properties

            try (ProjectConnection connection = connector.connect()) {
                connection.newBuild()
                    .forTasks(tasks)
                    .setStandardOutput(logStream)
                    .setStandardError(logStream)
                    .run();
                return;
            } catch (BuildException exception) {
                if (shouldRetry(exception)) {
                    log("Internet connection lost. Retrying in 5 seconds... (attempt %d)%n", attempt);
                    sleep(Duration.ofSeconds(5));
                    continue;
                }

                log("Gradle build failed: %s%n", exception.getMessage());

                throw new RuntimeException("Gradle build failed: " + exception.getMessage(), exception);
            }
        }
    }

    @Override
    public void setOutputStream(@NotNull OutputStream outputStream) {
        this.logStream = outputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return logStream;
    }

    private boolean shouldRetry(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketException || current instanceof SocketTimeoutException) {
                return true;
            }
            if (current instanceof UncheckedIOException uio && uio.getCause() != null) {
                current = uio.getCause();
                continue;
            }

            String message = current.getMessage();
            if (message != null && message.contains("Software caused connection abort")) {
                return true;
            }

            current = current.getCause();
        }
        return false;
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private void log(String message, Object... args) {
        if (logStream == null) {
            return;
        }

        String formatted = args.length == 0 ? message : String.format(message, args);
        try {
            logStream.write(formatted.getBytes(StandardCharsets.UTF_8));
            logStream.flush();
        } catch (IOException ignored) {
        }
    }
}
