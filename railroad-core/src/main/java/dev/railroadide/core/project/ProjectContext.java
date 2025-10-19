package dev.railroadide.core.project;

import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode
@ToString
public final class ProjectContext {
    private final ProjectData data;
    private final Path projectDir;

    private final Map<Key<?>, Object> extras = new HashMap<>();

    public ProjectContext(ProjectData data, Path projectDir) {
        this.data = data;
        this.projectDir = projectDir;
    }

    public ProjectData data() {
        return data;
    }

    public Path projectDir() {
        return projectDir;
    }

    public <T> void put(Key<T> key, T value) {
        extras.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Key<T> key) {
        return (T) this.extras.get(key);
    }

    public <T> T getOrDefault(Key<T> key, T fallback) {
        T v = get(key);
        return v != null ? v : fallback;
    }

    public record Key<T>(String name) {
    }
}
