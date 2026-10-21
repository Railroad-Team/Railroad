package dev.railroadide.railroad.vcs;

import dev.railroadide.railroad.plugin.spi.services.VCSService;
import dev.railroadide.railroad.vcs.connections.AbstractConnection;
import dev.railroadide.railroad.vcs.connections.VCSProfile;
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
    /**
     * Live observable collection of the managed VCS profiles.
     *
     * @return the mutable list of profiles
     */
    private final ObservableList<VCSProfile> profiles = FXCollections.observableArrayList();

    /** Creates a repository manager with no VCS profiles. */
    public RepositoryManager() {
    }

    /**
     * Creates a connection for each profile, fetches its repositories, and combines their current lists.
     *
     * @return a new list containing the repositories exposed by each connection after its fetch call
     */
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

    /**
     * Adds a profile if it is non-null and not already present.
     *
     * @param profile profile to add; null is ignored
     */
    @Override
    public void addProfile(VCSProfile profile) {
        if (profile != null && !profiles.contains(profile)) {
            profiles.add(profile);
        }
    }

    /**
     * Removes a profile if present.
     *
     * @param profile profile to remove; null is ignored
     */
    @Override
    public void removeProfile(VCSProfile profile) {
        if (profile != null) {
            profiles.remove(profile);
        }
    }
}
