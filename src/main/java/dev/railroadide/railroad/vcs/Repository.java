package dev.railroadide.railroad.vcs;

import dev.railroadide.railroad.vcs.connections.AbstractConnection;
import javafx.scene.image.Image;
import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a repository in a version control system (VCS).
 * This class contains information about the repository, such as its type, URL, name,
 * and associated connection, and provides functionality to clone the repository.
 */
@Setter
@Getter
public class Repository {
    /**
     * The URL of the repository.
     *
     * @param repositoryURL repository URL to store
     * @return the repository URL, or null when unset
     */
    private String repositoryURL;

    /**
     * The clone URL of the repository, used for cloning operations.
     *
     * @param repositoryCloneURL clone URL to store
     * @return the clone URL, or null when unset
     */
    private String repositoryCloneURL;

    /**
     * An optional icon representing the repository.
     *
     * @param icon replacement repository icon
     * @return the configured icon container, initially empty
     */
    private Optional<Image> icon = Optional.empty();

    /**
     * The name of the repository.
     *
     * @param repositoryName display name to store
     * @return the repository name, or null when unset
     */
    private String repositoryName;

    /**
     * The connection associated with this repository.
     *
     * @param connection connection responsible for repository operations
     * @return the connection, or null when unset
     */
    private AbstractConnection connection;

    /** Creates a repository with no configured URLs, name, or connection and an empty icon. */
    public Repository() {
    }

    /**
     * Clones the repository to the specified local path.
     *
     * @param path The local file system path where the repository will be cloned.
     * @return A CompletableFuture that resolves to true if the cloning operation
     *         was successful, or false otherwise.
     */
    public CompletableFuture<Boolean> cloneRepo(Path path) {
        return this.connection.cloneRepo(this, path);
    }
}
