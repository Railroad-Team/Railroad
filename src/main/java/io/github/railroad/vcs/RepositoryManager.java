package io.github.railroad.vcs;

import io.github.railroad.Railroad;
import io.github.railroad.vcs.connections.AbstractConnection;
import io.github.railroad.vcs.connections.Profile;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;

public class RepositoryManager extends Thread {
    private final ObservableList<Repository> repositoryList;
    private final ObservableList<AbstractConnection> connections;

    public RepositoryManager() {
        this.connections = FXCollections.observableArrayList();
        this.repositoryList = FXCollections.observableArrayList();
    }

    @Override
    public void run() {
        while (true) {
            try {
                this.updateRepositories();
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Railroad.LOGGER.error("RepositoryManager interrupted", e);
                Thread.currentThread().interrupt(); // Restore interrupted status
                break;
            }
        }
    }

    public ObservableList<Repository> getRepositoryList() {
        return repositoryList;
    }

    public void updateRepositories() {
        try {
            List<Repository> newRepositories = new ArrayList<>();
            for (AbstractConnection abstractConnection : connections) {
                newRepositories.addAll(abstractConnection.getRepositories());
            }
            repositoryList.setAll(newRepositories);
        } catch (Exception exception) {
            Railroad.LOGGER.error("Error updating repositories", exception);
        }
    }

    public boolean addConnection(AbstractConnection connection) {
        this.connections.add(connection);
        return true;
    }
    public ObservableList<Profile> getProfiles() {
        ObservableList<Profile> profiles = FXCollections.observableArrayList();
        for (AbstractConnection connection : connections) {
            profiles.addAll(connection.getProfile());
        }
        return profiles;
    }

}
