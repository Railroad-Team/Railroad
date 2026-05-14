package dev.railroadide.railroad.plugin.spi.deps;

import org.jspecify.annotations.NonNull;

/**
 * Represents a Maven repository.
 * This record holds the details of a Maven repository including its ID, and URL.
 */
public record MavenRepo(String id, String url) {
    /**
     * Creates a new Maven repository.
     *
     * @param id  the unique identifier of the repository
     * @param url the URL of the repository
     */
    public MavenRepo {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Repository ID cannot be null or blank");

        if (url == null || url.isBlank())
            throw new IllegalArgumentException("Repository URL cannot be null or blank");
    }

    @Override
    public @NonNull String toString() {
        return "MavenRepo{" +
                "id='" + id + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
