package io.github.railroad.vcs;

import io.github.railroad.Railroad;
import io.github.railroad.config.ConfigHandler;
import io.github.railroad.plugin.defaults.Github;
import io.github.railroad.utility.ShutdownHooks;
import io.github.railroad.vcs.connections.AbstractConnection;
import io.github.railroad.vcs.connections.Profile;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RepositoryManager implements Runnable {
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    private final ObservableList<AbstractConnection> connections = FXCollections.observableArrayList();
    @Getter
    private final ObservableList<Repository> repositories = FXCollections.observableArrayList();

    @Override
    public void run() {
        Railroad.LOGGER.debug("Starting update repos");
        updateRepositories();
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

//    public ObservableList<Profile> getProfiles() {
//        Github.GithubSettings settings = ConfigHandler.getConfig().getSettings().getPluginSettings("Github", Github.GithubSettings.class);
//        return settings.getAccounts();
//    }

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

    public void start() {
        EXECUTOR.scheduleAtFixedRate(this, 0, 60, TimeUnit.SECONDS);
        ShutdownHooks.addHook(EXECUTOR::shutdownNow);
    }
}
