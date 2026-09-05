package dev.railroadide.railroad.project;

import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the context of a project, containing project data, the project directory, and additional key-value pairs.
 * This class is immutable and provides methods to access the project data, project directory, and additional
 * information stored in the context.
 */
@EqualsAndHashCode
@ToString
public final class ProjectContext {
    private final ProjectData data;
    private final Path projectDir;

    private final Map<Key<?>, Object> extras = new HashMap<>();

    /**
     * Constructs a new ProjectContext with the given data and project directory.
     *
     * @param data The project data.
     * @param projectDir The path to the project directory.
     */
    public ProjectContext(ProjectData data, Path projectDir) {
        this.data = data;
        this.projectDir = projectDir;
    }

    /**
     * Returns the project data.
     *
     * @return The project data.
     */
    public ProjectData data() {
        return data;
    }

    /**
     * Returns the path to the project directory.
     *
     * @return The path to the project directory.
     */
    public Path projectDir() {
        return projectDir;
    }

    /**
     * Stores a value in the context associated with the given key.
     *
     * @param key The key to associate with the value.
     * @param value The value to store.
     * @param <T> The type of the value.
     */
    public <T> void put(Key<T> key, T value) {
        extras.put(key, value);
    }

    /**
     * Retrieves a value from the context associated with the given key.
     *
     * @param key The key to retrieve the value for.
     * @param <T> The type of the value.
     * @return The value associated with the key, or null if not found.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Key<T> key) {
        return (T) this.extras.get(key);
    }

    /**
     * Retrieves a value from the context associated with the given key, or returns a fallback value if not found.
     *
     * @param key The key to retrieve the value for.
     * @param fallback The fallback value to return if the key is not found.
     * @param <T> The type of the value.
     * @return The value associated with the key, or the fallback value if not found.
     */
    public <T> T getOrDefault(Key<T> key, T fallback) {
        T v = get(key);
        return v != null ? v : fallback;
    }

    /**
     * A key for storing and retrieving values in the ProjectContext.
     *
     * @param name The name of the key.
     * @param <T> The type of the value associated with this key.
     */
    public record Key<T>(String name) {
    }
}
