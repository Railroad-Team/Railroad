package dev.railroadide.railroad.utility;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Utility class for Git operations.
 */
public class GitUtils {
    /**
     * Clones a Git repository from the specified URI to the given destination path.
     *
     * @param uri  The URI of the Git repository to clone.
     * @param dest The destination path where the repository should be cloned.
     * @return A CompletableFuture that completes with true if the clone was successful, false otherwise.
     */
    public static CompletableFuture<Boolean> clone(String uri, Path dest) {
        return CompletableFuture.supplyAsync(() -> {
            try (Git git = Git.cloneRepository()
                    .setURI(uri)
                    .setDirectory(dest.toFile())
                    .call()) {
                return git.getRepository().getDirectory() != null;
            } catch (GitAPIException exception) {
                throw new RuntimeException("Failed to clone repository from " + uri + " to " + dest, exception);
            }
        });
    }

    /**
     * Checks if the given path is a valid Git repository.
     *
     * @param path The path to check.
     * @return True if the path is a valid Git repository, false otherwise.
     */
    public static boolean isGitRepository(Path path) {
        try (Git git = Git.open(path.toFile())) {
            return git.getRepository().getDirectory() != null;
        } catch (Exception ignored) {
            return false;
        }
    }
}
