package dev.railroadide.railroad.project.creation.service;

import java.io.OutputStream;
import java.nio.file.Path;

/**
 * Executes Gradle tasks and configures their output destination during project creation.
 */
public interface GradleService {
    /**
     * Runs gradle tasks inside a given project directory.
     *
     * @param projectDir the Gradle project directory
     * @param tasks the task names to execute
     * @throws Exception if the build cannot be started or a task fails
     */
    void runTasks(Path projectDir, String... tasks) throws Exception;

    /**
     * Sets the destination for Gradle build output.
     *
     * @param outputStream the stream to receive build output
     */
    void setOutputStream(OutputStream outputStream);

    /**
     * Returns the configured destination for Gradle build output.
     *
     * @return the configured output stream
     */
    OutputStream getOutputStream();
}
