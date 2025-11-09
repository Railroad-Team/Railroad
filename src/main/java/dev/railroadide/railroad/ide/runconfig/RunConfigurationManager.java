package dev.railroadide.railroad.ide.runconfig;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.project.data.ProjectDataStore;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RunConfigurationManager {
    private static final String RUN_CONFIGURATIONS_FILE = "run_configs.json";

    @Getter
    private final ObservableList<RunConfiguration<?>> configurations = FXCollections.observableArrayList();
    private final Project project;

    public RunConfigurationManager(Project project) {
        this.project = project;
        readConfigurations();

        this.configurations.addListener(
            (ListChangeListener<? super RunConfiguration<?>>) change -> writeRunConfigurations());
    }

    /**
     * Remove a run configuration from this project.
     *
     * @param configuration The run configuration to remove.
     */
    public boolean removeConfiguration(RunConfiguration<?> configuration) {
        return this.configurations.remove(configuration);
    }

    /**
     * Add a run configuration to this project.
     *
     * @param configuration The run configuration to add.
     * @throws IllegalArgumentException if a run configuration with the same name already exists.
     */
    public void addConfiguration(RunConfiguration<?> configuration) {
        if (this.configurations.contains(configuration))
            throw new IllegalArgumentException("Run configuration already exists: " + configuration.data().getName());

        this.configurations.addFirst(configuration);
    }

    /**
     * Update an existing run configuration.
     *
     * @param configuration The run configuration to update.
     */
    public @Nullable RunConfiguration<?> updateConfiguration(@NotNull RunConfiguration<?> configuration) {
        int index = getIndexByUUID(configuration.uuid());
        return index != -1 ? this.configurations.set(index, configuration) : null;

    }

    /**
     * Get the index of a run configuration by its UUID.
     *
     * @param uuid The UUID of the run configuration.
     * @return The index of the run configuration, or -1 if not found.
     */
    public int getIndexByUUID(UUID uuid) {
        for (int index = 0; index < this.configurations.size(); index++) {
            if (this.configurations.get(index).uuid().equals(uuid))
                return index;
        }

        return -1;
    }

    /**
     * Sends updated configurations to this manager, replacing the current list.
     *
     * @param configurations The new list of run configurations.
     */
    public void sendUpdatedConfigurations(ObservableList<RunConfiguration<?>> configurations) {
        this.configurations.setAll(configurations);
    }

    /**
     * Get a run configuration by its UUID.
     *
     * @param uuid The UUID of the run configuration.
     * @return The run configuration, or null if not found.
     */
    public @Nullable RunConfiguration<?> getConfigurationByUUID(@Nullable UUID uuid) {
        return this.configurations.stream()
            .filter(config -> config.uuid().equals(uuid))
            .findFirst()
            .orElse(null);
    }

    /**
     * Checks if a run configuration name is duplicate within the same type.
     *
     * @param name         The name to check.
     * @param type         The run configuration type.
     * @param excludeUUIDs UUIDs to exclude from the check.
     * @return True if the name can be accepted, false otherwise.
     */
    public boolean isDuplicateName(String name, RunConfigurationType<?> type, UUID... excludeUUIDs) {
        return this.configurations.stream().anyMatch(
            cfg -> cfg.data().getName().equalsIgnoreCase(name) && cfg.type() == type &&
                (excludeUUIDs == null || excludeUUIDs.length == 0 || !List.of(excludeUUIDs).contains(cfg.uuid())));
    }

    /**
     * Writes the current run configurations to the project's data store.
     */
    private void writeRunConfigurations() {
        var jsonArray = new JsonArray();
        for (RunConfiguration<?> config : this.configurations) {
            jsonArray.add(RunConfiguration.toJson(config));
        }

        this.project.getDataStore().writeJson(RUN_CONFIGURATIONS_FILE, jsonArray);
    }

    /**
     * Reads the run configurations from the project's data store and populates the configurations list.
     */
    private void readConfigurations() {
        ProjectDataStore dataStore = this.project.getDataStore();
        dataStore.readJson(RUN_CONFIGURATIONS_FILE, JsonArray.class).ifPresent(jsonArray -> {
            List<RunConfiguration<?>> runConfigurations = new ArrayList<>(jsonArray.size());
            for (JsonElement element : jsonArray) {
                try {
                    RunConfiguration<?> config = RunConfiguration.fromJson(element.getAsJsonObject());
                    runConfigurations.add(config);
                } catch (Exception exception) {
                    Railroad.LOGGER.error("Failed to load run configuration for project: {}", this.project.getPathString(), exception);
                }
            }

            this.configurations.setAll(runConfigurations);
        });
    }
}
