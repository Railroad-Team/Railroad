package io.github.railroad.vcs.connections;

import io.github.railroad.vcs.Repository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public abstract class AbstractConnection {
    private ObservableList<Repository> repositories = FXCollections.observableArrayList();

    public abstract void downloadRepositories();

    public abstract boolean updateRepo(Repository repo);

    public abstract void cloneRepo(Repository repository);

    public abstract boolean validateProfile();

    public abstract Profile getProfile();

    public ObservableList<Repository> getRepositories() {
        return repositories;
    }
}
