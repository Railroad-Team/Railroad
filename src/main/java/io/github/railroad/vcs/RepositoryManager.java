package io.github.railroad.vcs;

import io.github.railroad.Railroad;
import io.github.railroad.vcs.connections.AbstractConnection;
import io.github.railroad.vcs.connections.Profile;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;

public class RepositoryManager extends Thread {
    private final ObservableList<AbstractConnection> connections = FXCollections.observableArrayList();
    private final ObservableList<Repository> repositories = FXCollections.observableArrayList();

    @Override
    public void run() {
        while (true) {
            try {
                Railroad.LOGGER.debug("Starting update repos");
                updateRepositories();
                Thread.sleep(60_000);
            } catch (InterruptedException exception) {
                Railroad.LOGGER.error("RepositoryManager interrupted", exception);
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

    public List<Profile> getProfiles() {
        List<Profile> profiles = new ArrayList<>();
        for (AbstractConnection connection : connections) {
            if (!connection.getProfile().toDelete()) {
                profiles.add(connection.getProfile());
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
