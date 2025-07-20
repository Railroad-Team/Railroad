package io.github.railroad.core.vcs.connections;

import io.github.railroad.core.vcs.Repository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * An abstract class representing a connection to a version control system (VCS).
 * This class provides a base for managing repositories and performing VCS operations.
 */
@Getter
public abstract class AbstractConnection {
    /**
     * A list of repositories associated with this connection.
     * This list is observable, allowing UI components or other listeners
     * to react to changes in the repository collection.
     */
    protected final ObservableList<Repository> repositories = FXCollections.observableArrayList();

    /**
     * Fetches the repositories associated with this connection.
     * This method must be implemented by subclasses to define the specific
     * behavior for retrieving repositories from the VCS.
     */
    public abstract void fetchRepositories();

    /**
     * Clones a repository to the specified local path.
     * This method must be implemented by subclasses to define the specific
     * behavior for cloning a repository from the VCS.
     *
     * @param repository The repository to clone.
     * @param path       The local file system path where the repository will be cloned.
     * @return A CompletableFuture that resolves to true if the cloning operation
     * was successful, or false otherwise.
     */
    public abstract CompletableFuture<Boolean> cloneRepo(Repository repository, Path path);
}