package dev.railroadide.core.vcs.connections;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * An abstract class representing a profile for a version control system (VCS).
 * This class provides a base for defining VCS profiles, including their alias
 * and methods for creating connections and retrieving profile types.
 */
public abstract class VCSProfile {
    /**
     * The alias of the VCS profile, represented as a JavaFX StringProperty.
     * This allows for binding and observing changes to the alias.
     */
    private final StringProperty alias = new SimpleStringProperty();

    /**
     * Retrieves the alias of the VCS profile.
     *
     * @return The alias as a String.
     */
    public String getAlias() {
        return alias.get();
    }

    /**
     * Provides access to the alias property.
     * This can be used for binding or observing changes to the alias.
     *
     * @return The alias as a StringProperty.
     */
    public StringProperty aliasProperty() {
        return alias;
    }

    /**
     * Creates a connection to the version control system.
     * Subclasses must implement this method to define the specific behavior
     * for establishing a connection.
     *
     * @return An instance of AbstractConnection representing the VCS connection.
     */
    public abstract AbstractConnection createConnection();

    /**
     * Retrieves the type of the VCS profile.
     * Subclasses must implement this method to define the specific profile type.
     *
     * @return The type of the profile as a ProfileType.
     */
    public abstract ProfileType getType();
}