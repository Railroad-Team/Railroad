package io.github.railroad.vcs;

import io.github.railroad.Railroad;
import io.github.railroad.vcs.connections.AbstractConnection;
import io.github.railroad.vcs.connections.Profile;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RepositoryManager extends Thread {
    private final ObservableList<AbstractConnection> connections = FXCollections.observableArrayList();
    @Getter
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
                if (abstractConnection.getProfile().isToDelete()) {
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

    public void addConnection(AbstractConnection connection) {
        this.connections.add(connection);
    }

    public List<Profile> getProfiles() {
        List<Profile> profiles = new ArrayList<>();
        for (AbstractConnection connection : connections) {
            if (!connection.getProfile().isToDelete()) {
                profiles.add(connection.getProfile());
            }
        }
        
        return profiles;
    }

    public boolean deleteProfile(Profile profile) {
        for (AbstractConnection connection : connections) {
            if (Objects.equals(connection.getProfile(), profile)) {
                connection.getProfile().markForDeletion();
                Railroad.LOGGER.info("VCS - Marking for delete profile: {}", profile.getAlias());
                return true;
            }
        }

        return false;
    }
}
