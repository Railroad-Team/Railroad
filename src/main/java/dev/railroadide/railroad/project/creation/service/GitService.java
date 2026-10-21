package dev.railroadide.railroad.project.creation.service;

import java.nio.file.Path;

/**
 * Initializes version control for a newly created project.
 */
public interface GitService {
    /**
     * Initializes a git repository in the given path.
     *
     * @param repoDir the directory in which to initialize the repository
     * @throws Exception if the repository cannot be initialized
     */
    void init(Path repoDir) throws Exception;

    /**
     * Stages the project's initial files and commits them.
     *
     * @param repoDir the directory containing the repository
     * @param message the initial commit message
     * @throws Exception if the files cannot be staged or committed
     */
    void initialCommit(Path repoDir, String message) throws Exception;
}
