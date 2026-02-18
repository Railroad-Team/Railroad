package dev.railroadide.railroad.plugin.spi.dto;

import java.nio.file.Path;

/**
 * Represents a project in the Railroad plugin API.
 * This interface provides methods to retrieve the project's alias, and path.
 */
public interface Project {
    /**
     * Gets the alias of the project.
     *
     * @return the alias of the project
     */
    String getAlias();

    /**
     * Gets the path of the project.
     *
     * @return the path of the project
     */
    Path getPath();

    /**
     * Sets the alias of the project.
     *
     * @param alias the new alias for the project
     */
    void setAlias(String alias);
}
