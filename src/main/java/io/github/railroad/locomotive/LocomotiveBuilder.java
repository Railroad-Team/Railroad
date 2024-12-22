package io.github.railroad.locomotive;

import io.github.railroad.config.ConfigHandler;
import io.github.railroad.vcs.Repository;
import io.github.railroad.vcs.RepositoryTypes;
import org.gradle.api.JavaVersion;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.ResultHandler;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class LocomotiveBuilder {
    private final JavaVersion javaVersion;
    private final Path javaPath;
    private String gitUrl = "https://github.com/Railroad-Team/Locomotive";
    private String executableName = "Locomotive";
    private Path clonePath;

    private LocomotiveBuilder(@NotNull JavaVersion javaVersion, @NotNull Path javaPath) {
        Objects.requireNonNull(javaVersion);
        Objects.requireNonNull(javaPath);

        this.javaVersion = javaVersion;
        this.javaPath = javaPath;
        this.clonePath = ConfigHandler.getConfigDirectory().resolve("Locomotive-" + javaPath);
    }

    public static LocomotiveBuilder begin(JavaVersion javaVersion, Path javaPath) {
        return new LocomotiveBuilder(javaVersion, javaPath);
    }

    public LocomotiveBuilder withExecutableName(@NotNull String executableName) {
        Objects.requireNonNull(executableName);

        if (executableName.isBlank())
            throw new IllegalArgumentException("Executable name cannot be blank");

        // validate executable name
        try {
            Path.of(executableName);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid executable name");
        }

        this.executableName = executableName;
        return this;
    }

    public LocomotiveBuilder withGitUrl(@NotNull String gitUrl) {
        Objects.requireNonNull(gitUrl);

        if (gitUrl.isBlank())
            throw new IllegalArgumentException("Git URL cannot be blank");

        if (gitUrl.endsWith(".git") || (gitUrl.startsWith("https://"))) // TODO: Config to allow http (?)
            throw new IllegalArgumentException("Invalid Git URL");

        this.gitUrl = gitUrl;
        return this;
    }

    public LocomotiveBuilder withClonePath(@NotNull Path clonePath) {
        Objects.requireNonNull(clonePath);

        if (!Files.isDirectory(clonePath))
            throw new IllegalArgumentException("Invalid clone path");

        this.clonePath = clonePath;
        return this;
    }

    public CompletableFuture<Path> createExecutable() {
        if (javaVersion == null || gitUrl == null || clonePath == null)
            throw new IllegalStateException("Missing required fields");

        var repository = new Repository(RepositoryTypes.GIT);
        repository.setRepositoryCloneURL(gitUrl);
        repository.setRepositoryName(executableName);

        if (!repository.cloneRepo(clonePath))
            throw new IllegalStateException("Failed to clone repository");

        CompletableFuture<Path> future = new CompletableFuture<>();
        try (ProjectConnection connection = GradleConnector.newConnector()
                .forProjectDirectory(clonePath.toFile())
                .connect()) {
            connection.newBuild()
                    .forTasks("launch4j")
                    .setJavaHome(javaPath.toFile())
                    .run(new ResultHandler<>() {
                        @Override
                        public void onComplete(Void result) {
                            future.complete(clonePath.resolve("build").resolve("launch4j").resolve(executableName + ".exe"));
                        }

                        @Override
                        public void onFailure(GradleConnectionException failure) {
                            future.completeExceptionally(failure);
                        }
                    });
        }

        return future;
    }
}
