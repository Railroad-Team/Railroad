package io.github.railroad.vcs;

import io.github.railroad.Railroad;
import io.github.railroad.vcs.connections.AbstractConnection;
import io.github.railroad.vcs.connections.Profile;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;

public class RepositoryManager extends Thread {
    private final ObservableList<AbstractConnection> connections;
    private final ObservableList<Repository> repositories = FXCollections.observableArrayList();

    public RepositoryManager() {
        this.connections = FXCollections.observableArrayList();
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


    public void updateRepositories() {
        try {
            for (AbstractConnection abstractConnection : connections) {
                if (abstractConnection.getProfile().toDelete()) {
                    connections.remove(abstractConnection);
                    continue;
                }
                abstractConnection.downloadRepositories();
                repositories.setAll(abstractConnection.getRepositories());
            }

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
            if (!connection.getProfile().toDelete()) {
                profiles.addAll(connection.getProfile());
            }
        }
        return profiles;
    }

    public ObservableList<Repository> getRepositories() {
        return repositories;
    }

    public boolean deleteWhereProfileIs(Profile profile) {
        for (AbstractConnection connection : connections) {
            if (connection.getProfile() == profile) {
                connection.getProfile().markDelete();
                Railroad.LOGGER.info("VCS - Marking for delete profile:" + profile.getAlias());
                return true;
            }
        }
        return false;
    }
}
