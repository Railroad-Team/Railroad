package io.github.railroad.vcs;

import io.github.railroad.core.vcs.Repository;
import io.github.railroad.core.vcs.connections.AbstractConnection;
import io.github.railroad.core.vcs.connections.VCSProfile;
import io.github.railroad.railroadpluginapi.services.VCSService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages VCS profiles and repositories.
 * This class provides methods to list repositories, add and remove profiles.
 */
@Getter
public class RepositoryManager implements VCSService {
    private final ObservableList<VCSProfile> profiles = FXCollections.observableArrayList();

    @Override
    public List<Repository> listRepositories() {
        List<Repository> repositories = new ArrayList<>();
        for (VCSProfile profile : profiles) {
            AbstractConnection connection = profile.createConnection();
            connection.fetchRepositories();
            repositories.addAll(connection.getRepositories());
        }

        return repositories;
    }

    @Override
    public void addProfile(VCSProfile profile) {
        if (profile != null && !profiles.contains(profile)) {
            profiles.add(profile);
        }
    }

    @Override
    public void removeProfile(VCSProfile profile) {
        if (profile != null) {
            profiles.remove(profile);
        }
    }
}
