package dev.railroadide.railroad.plugin.spi.services;

/**
 * Service to provide application information such as version, name, and build timestamp.
 */
public interface ApplicationInfoService {
    /**
     * Retrieves the version of the application.
     *
     * @return the version string.
     */
    String getVersion();

    /**
     * Retrieves the name of the application.
     *
     * @return the name string.
     */
    String getName();

    /**
     * Retrieves the build timestamp of the application.
     *
     * @return the build timestamp string.
     */
    String getBuildTimestamp();
}
