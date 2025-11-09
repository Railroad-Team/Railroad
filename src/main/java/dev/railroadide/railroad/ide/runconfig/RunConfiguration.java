package dev.railroadide.railroad.ide.runconfig;

import com.google.gson.JsonObject;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.Project;
import javafx.scene.control.ContextMenu;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a single run configuration entry that can be exposed through the IDE toolbar.
 * A configuration stores presentation metadata and callbacks for run, debug, and stop actions.
 */
@EqualsAndHashCode
@ToString
public final class RunConfiguration<D extends RunConfigurationData> {
    private final UUID uuid;
    private final RunConfigurationType<D> type;
    private final D data;
    private String folderPath;

    /**
     * Creates a new run configuration.
     *
     * @param uuid the unique identifier of the configuration
     * @param type the type of the configuration
     * @param data the data of the configuration
     */
    public RunConfiguration(UUID uuid, RunConfigurationType<D> type, D data) {
        this.uuid = uuid;
        this.type = type;
        this.data = data;
    }

    /**
     * Creates a new run configuration with a random UUID.
     *
     * @param type the type of the configuration
     * @param data the data of the configuration
     */
    public RunConfiguration(RunConfigurationType<D> type, D data) {
        this(UUID.randomUUID(), type, data);
    }

    /**
     * Gets the unique identifier of the configuration.
     *
     * @return the UUID
     */
    public UUID uuid() {
        return uuid;
    }

    /**
     * Gets the type of the configuration.
     *
     * @return the type
     */
    public RunConfigurationType<D> type() {
        return type;
    }

    /**
     * Gets the data of the configuration.
     *
     * @return the data
     */
    public D data() {
        return data;
    }

    /**
     * Gets the folder path of the configuration.
     *
     * @return the folder path
     */
    public String folderPath() {
        return folderPath;
    }

    /**
     * Sets the folder path of the configuration.
     *
     * @param folderPath the folder path to set
     * @throws NullPointerException if folderPath is null
     */
    public void setFolderPath(@NotNull String folderPath) {
        Objects.requireNonNull(folderPath, "folderPath");
        this.folderPath = folderPath;
    }

    /**
     * Runs the configuration in the given project.
     *
     * @param project the project to run the configuration in
     * @return a completable future that completes when the run action is finished
     */
    public CompletableFuture<Void> run(Project project) {
        Objects.requireNonNull(project, "project");
        return type.run(project, this);
    }

    /**
     * Runs the configuration in the given project.
     *
     * @param project the project to run the configuration in
     * @return a completable future that completes when the run action is finished
     */
    public CompletableFuture<Void> debug(Project project) {
        Objects.requireNonNull(project, "project");
        return type.debug(project, this);
    }

    /**
     * Stops the configuration in the given project.
     *
     * @param project the project to stop the configuration in
     * @return a completable future that completes when the stop action is finished
     */
    public CompletableFuture<Void> stop(Project project) {
        Objects.requireNonNull(project, "project");
        return type.stop(project, this);
    }

    /**
     * Checks if running is supported for this configuration in the given project.
     *
     * @param project the project to check
     * @return true if running is supported, false otherwise
     */
    public boolean isDebuggingSupported(Project project) {
        Objects.requireNonNull(project, "project");
        return type.isDebuggingSupported(project, this);
    }

    /**
     * Creates a context menu for this configuration in the given project.
     *
     * @param project the project to create the context menu for
     * @return the context menu
     */
    public ContextMenu createContextMenu(Project project) {
        Objects.requireNonNull(project, "project");
        return type.createContextMenu(project, this);
    }

    /**
     * Serializes the given run configuration to a JSON object.
     *
     * @param config the run configuration to serialize
     * @return the JSON object representing the run configuration
     */
    public static JsonObject toJson(RunConfiguration<?> config) {
        var json = new JsonObject();
        json.addProperty("uuid", config.uuid().toString());
        json.addProperty("type", RunConfigurationType.REGISTRY.entries().entrySet().stream()
            .filter(entry -> Objects.equals(entry.getValue(), config.type()))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(config.type().getClass().getSimpleName() + " not registered in registry")));
        json.add("data", Railroad.GSON.toJsonTree(config.data()));
        return json;
    }

    /**
     * Deserializes a run configuration from a JSON object.
     *
     * @param json the JSON object to deserialize
     * @return the deserialized run configuration
     */
    @SuppressWarnings("unchecked")
    public static <D extends RunConfigurationData> RunConfiguration<D> fromJson(JsonObject json) {
        UUID uuid = UUID.fromString(requireNonBlank(json.get("uuid").getAsString(), "uuid"));
        String typeId = requireNonBlank(json.get("type").getAsString(), "type");
        RunConfigurationType<D> type = (RunConfigurationType<D>) RunConfigurationType.REGISTRY.get(typeId);
        if (type == null)
            throw new IllegalStateException("RunConfigurationType with id '" + typeId + "' not found in registry");

        D data = Railroad.GSON.fromJson(json.get("data"), type.getDataClass());
        return new RunConfiguration<>(uuid, type, data);
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank())
            throw new IllegalArgumentException(name + " cannot be blank");

        return value;
    }
}
