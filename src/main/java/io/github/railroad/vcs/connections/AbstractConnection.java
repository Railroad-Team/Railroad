package io.github.railroad.vcs.connections;

import io.github.railroad.vcs.Repository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.nio.file.Path;

public abstract class AbstractConnection {
    private final ObservableList<Repository> repositories = FXCollections.observableArrayList();

    public abstract void downloadRepositories();

    public abstract boolean updateRepo(Repository repo);

    public abstract boolean cloneRepo(Repository repository, Path path);

    public abstract boolean validateProfile();

    public abstract Profile getProfile();

    public ObservableList<Repository> getRepositories() {
        return repositories;
    }
}
