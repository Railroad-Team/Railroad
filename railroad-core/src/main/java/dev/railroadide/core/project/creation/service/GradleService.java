package dev.railroadide.core.project.creation.service;

import java.io.OutputStream;
import java.nio.file.Path;

public interface GradleService {
    /**
     * Runs gradle tasks inside a given project directory.
     */
    void runTasks(Path projectDir, String... tasks) throws Exception;

    void setOutputStream(OutputStream outputStream);

    OutputStream getOutputStream();
}
