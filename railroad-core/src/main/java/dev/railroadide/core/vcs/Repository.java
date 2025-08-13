package dev.railroadide.core.vcs;

import dev.railroadide.core.vcs.connections.AbstractConnection;
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
     * The type of the repository (e.g., Git, SVN).
     */
    private RepositoryTypes repositoryType;

    /**
     * The URL of the repository.
     */
    private String repositoryURL;

    /**
     * The clone URL of the repository, used for cloning operations.
     */
    private String repositoryCloneURL;

    /**
     * An optional icon representing the repository.
     */
    private Optional<Image> icon = Optional.empty();

    /**
     * The name of the repository.
     */
    private String repositoryName;

    /**
     * The connection associated with this repository.
     */
    private AbstractConnection connection;

    /**
     * Constructs a new Repository with the specified repository type.
     *
     * @param repositoryType The type of the repository.
     */
    public Repository(RepositoryTypes repositoryType) {
        setRepositoryType(repositoryType);
    }

    /**
     * Clones the repository to the specified local path.
     *
     * @param path The local file system path where the repository will be cloned.
     * @return A CompletableFuture that resolves to true if the cloning operation
     * was successful, or false otherwise.
     */
    public CompletableFuture<Boolean> cloneRepo(Path path) {
        return this.connection.cloneRepo(this, path);
    }
}