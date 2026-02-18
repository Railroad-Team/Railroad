package dev.railroadide.railroad.plugin.spi.services;

import dev.railroadide.railroad.vcs.Repository;
import dev.railroadide.railroad.vcs.connections.VCSProfile;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service interface for managing Version Control System (VCS) profiles and repositories.
 * This service allows listing repositories, adding and removing VCS profiles.
 */
public interface VCSService {
    /**
     * Lists all repositories available in the connected VCS profiles.
     *
     * @return a list of repositories.
     */
    List<Repository> listRepositories();

    /**
     * Adds a VCS profile to the service.
     *
     * @param profile the VCS profile to add.
     */
    void addProfile(VCSProfile profile);

    /**
     * Removes a VCS profile from the service.
     *
     * @param profile the VCS profile to remove.
     */
    void removeProfile(VCSProfile profile);

    /**
     * Retrieves a list of all VCS profiles.
     *
     * @return an observable list of VCS profiles.
     */
    ObservableList<VCSProfile> getProfiles();

    /**
     * Retrieves a list of VCS profiles of a specific type.
     *
     * @param profileType the class type of the VCS profile to retrieve.
     * @param <T> the type of the VCS profile.
     * @return a list of VCS profiles of the specified type.
     */
    default <T extends VCSProfile> ObservableList<T> getProfiles(Class<T> profileType) {
        return getProfiles().filtered(profileType::isInstance)
                .stream()
                .map(profileType::cast)
                .collect(Collectors.toCollection(FXCollections::observableArrayList));
    }
}
