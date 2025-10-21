package dev.railroadide.core.project.creation.service;

import java.nio.file.Path;

public interface GitService {
    /**
     * Initializes a git repository in the given path.
     */
    void init(Path repoDir) throws Exception;

    /**
     * Optionally add + commit initial files.
     */
    void initialCommit(Path repoDir, String message) throws Exception;
}
