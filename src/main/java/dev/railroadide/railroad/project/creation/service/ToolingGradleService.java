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
import java.nio.file.Path;
import java.util.Objects;

@EqualsAndHashCode
@ToString
@AllArgsConstructor
public final class ToolingGradleService implements GradleService {
    private OutputStream logStream;

    @Override
    public void runTasks(Path projectDir, String... tasks) throws RuntimeException, IOException {
        GradleConnector connector = GradleConnector.newConnector()
            .forProjectDirectory(projectDir.toFile())
            .useBuildDistribution(); // uses gradle.properties

        try (ProjectConnection connection = connector.connect()) {
            connection.newBuild()
                .forTasks(tasks)
                .setStandardOutput(logStream)
                .setStandardError(logStream)
                .run();
        } catch (BuildException exception) {
            logStream.write(exception.getMessage().getBytes());
            throw new RuntimeException("Gradle build failed: " + exception.getMessage(), exception);
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
}
