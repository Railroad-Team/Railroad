package dev.railroadide.railroad.ide.runconfig.defaults.data;

import dev.railroadide.core.form.*;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.gradle.GradleHelper;
import dev.railroadide.railroad.gradle.GradleTask;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationData;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationType;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationTypes;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.JDKManager;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.project.onboarding.ProjectValidators;
import dev.railroadide.railroad.settings.ui.DetectedJdkListPane;
import dev.railroadide.railroad.utility.StringUtils;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@EqualsAndHashCode(callSuper = true)
@Data
public class GradleRunConfigurationData extends RunConfigurationData {
    private String task;
    private Path gradleProjectPath; // TODO: A different data type(?) that represents a Gradle project model
    private Map<String, String> environmentVariables = new HashMap<>();
    private String[] vmOptions = new String[0];
    private JDK javaHome;

    @Override
    public RunConfigurationType<?> getType() {
        return RunConfigurationTypes.GRADLE;
    }

    @Override
    public Form createConfigurationForm(Project project, RunConfiguration<?> configuration) {
        ObjectProperty<ComboBox<JDK>> javaHomeComboBoxProperty = new SimpleObjectProperty<>();
        javaHomeComboBoxProperty.addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                newValue.setMinHeight(72);
                newValue.setPrefHeight(72);
                newValue.setMaxHeight(72);
            }
        });

        ObservableMap<Path, List<GradleTask>> gradleTasksCache = FXCollections.observableHashMap();
        ObjectProperty<Path> gradleProjectPathProperty = new SimpleObjectProperty<>(this.gradleProjectPath);
        gradleProjectPathProperty.addListener((observable, oldValue, newValue) ->
            loadGradleTasksAsync(newValue, gradleTasksCache));
        loadGradleTasksAsync(gradleProjectPathProperty.get(), gradleTasksCache);

        return createBaseFormBuilder(project, configuration)
            .appendSection(FormSection.create("railroad.runconfig.gradle.configuration.title")
                .appendComponent(FormComponent.textField("task", "railroad.runconfig.gradle.configuration.task.label")
                    .required()
                    .text(() -> this.task != null ? this.task : "")
                    .promptText("railroad.runconfig.gradle.configuration.task.prompt")
                    .autoCompleteSuggestionsSupplier(() ->
                        buildGradleTaskSuggestions(gradleProjectPathProperty.get(), gradleTasksCache))
                    .autoCompleteSuggestionCellFactory(createGradleTaskSuggestionCellFactory(
                        gradleProjectPathProperty, gradleTasksCache))
                    .autoCompleteShowSuggestionsOnEmpty(true)
                    .validator(textField -> {
                        String text = textField.getText();
                        if (text == null || text.isBlank())
                            return ValidationResult.error("railroad.runconfig.gradle.configuration.task.invalid");

                        // TODO: Use a gradle project model to validate the task
                        return ValidationResult.ok();
                    })
                    .build())
                .appendComponent(FormComponent.directoryChooser("gradleProjectPath", "railroad.runconfig.gradle.configuration.projectPath.label")
                    .required()
                    .defaultPath(this.gradleProjectPath)
                    .validator(ProjectValidators::validateGradleProjectPath)
                    .listener((node, observable, oldValue, newValue) -> {
                        Path normalizedPath = normalizeGradleProjectPath(newValue);
                        if (normalizedPath == null) {
                            gradleProjectPathProperty.set(null);
                            this.gradleProjectPath = null;
                            return;
                        }

                        if (!Files.isDirectory(normalizedPath))
                            return;

                        if (normalizedPath.equals(gradleProjectPathProperty.get()))
                            return;

                        gradleProjectPathProperty.set(normalizedPath);
                        this.gradleProjectPath = normalizedPath;
                    })
                    .build())
                .appendComponent(FormComponent.textField("environmentVariables", "railroad.runconfig.gradle.configuration.envVariables.label")
                    .required()
                    .text(() -> StringUtils.environmentVariablesToString(this.environmentVariables))
                    .promptText("railroad.runconfig.gradle.configuration.envVariables.prompt")
                    .validator(textField -> !StringUtils.isValidEnvironmentVariablesString(textField.getText()) ?
                        ValidationResult.error("railroad.runconfig.gradle.configuration.envVariables.invalid") :
                        ValidationResult.ok())
                    .build())
                .appendComponent(FormComponent.textField("vmOptions", "railroad.runconfig.gradle.configuration.vmOptions.label")
                    .required()
                    .text(() -> StringUtils.stringArrayToString(this.vmOptions, " "))
                    .promptText("railroad.runconfig.gradle.configuration.vmOptions.prompt")
                    .build())
                .appendComponent(FormComponent.comboBox("javaHome", "railroad.runconfig.gradle.configuration.javaHome.label", JDK.class)
                    .required()
                    .defaultValue(this::getJavaHome)
                    .items(JDKManager::getAvailableJDKs)
                    .translate(false)
                    .buttonCell(new DetectedJdkListPane.JdkCell())
                    .cellFactory($ -> new DetectedJdkListPane.JdkCell())
                    .keyFunction(jdk -> jdk != null ? jdk.path().toString() : "")
                    .valueOfFunction(jdkPath -> JDKManager.getAvailableJDKs()
                        .stream()
                        .filter(jdk -> jdk.path().toString().equals(jdkPath))
                        .findFirst()
                        .orElse(null))
                    .bindComboBoxTo(javaHomeComboBoxProperty)
                    .build())
            ).build();
    }

    private List<String> buildGradleTaskSuggestions(Path gradleProjectPath, ObservableMap<Path, List<GradleTask>> gradleTasksCache) {
        if (gradleProjectPath == null)
            return List.of();

        List<GradleTask> cachedTasks = gradleTasksCache.get(gradleProjectPath);
        if (cachedTasks == null || cachedTasks.isEmpty()) {
            Railroad.LOGGER.debug("No cached Gradle tasks for {} yet", gradleProjectPath);
            return List.of();
        }

        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        for (GradleTask task : cachedTasks) {
            if (task == null)
                continue;

            String taskName = task.name();
            if (taskName != null && !taskName.isBlank())
                suggestions.add(taskName);

            Map<String, String> options = task.options();
            if (options != null && !options.isEmpty())
                suggestions.addAll(options.keySet());
        }

        List<String> result = List.copyOf(suggestions);
        Railroad.LOGGER.debug("Providing {} suggestions for {}", result.size(), gradleProjectPath);
        return result;
    }

    private Callback<ListView<String>, ListCell<String>> createGradleTaskSuggestionCellFactory(
        ObjectProperty<Path> gradleProjectPathProperty,
        ObservableMap<Path, List<GradleTask>> gradleTasksCache) {
        return listView -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }

                String description = findGradleTaskDescription(item, gradleProjectPathProperty.get(), gradleTasksCache);
                if (description != null) {
                    setText(item + " - " + description);
                } else {
                    setText(item);
                }
            }
        };
    }

    private @Nullable String findGradleTaskDescription(String taskOrOptionName, Path gradleProjectPath,
                                                       ObservableMap<Path, List<GradleTask>> gradleTasksCache) {
        if (taskOrOptionName == null || gradleProjectPath == null)
            return null;

        List<GradleTask> tasks = gradleTasksCache.get(gradleProjectPath);
        if (tasks == null || tasks.isEmpty())
            return null;

        for (GradleTask task : tasks) {
            if (task == null)
                continue;

            if (taskOrOptionName.equals(task.name())) {
                String description = task.description();
                return description == null || description.isBlank() ? null : description;
            }

            Map<String, String> options = task.options();
            if (options != null && !options.isEmpty()) {
                String optionDescription = options.get(taskOrOptionName);
                if (optionDescription != null && !optionDescription.isBlank())
                    return optionDescription;
            }
        }

        return null;
    }

    private void loadGradleTasksAsync(Path gradleProjectPath, ObservableMap<Path, List<GradleTask>> gradleTasksCache) {
        if (gradleProjectPath == null) {
            if (Platform.isFxApplicationThread()) {
                gradleTasksCache.clear();
            } else {
                Platform.runLater(gradleTasksCache::clear);
            }

            return;
        }

        Railroad.LOGGER.debug("Loading Gradle tasks for {}", gradleProjectPath);
        CompletableFuture
            .supplyAsync(() -> GradleHelper.loadGradleTasks(gradleProjectPath))
            .whenComplete((tasks, throwable) -> {
                if (throwable != null) {
                    Railroad.LOGGER.warn("Failed to load Gradle tasks for autocomplete", throwable);
                    Platform.runLater(() -> gradleTasksCache.remove(gradleProjectPath));
                    return;
                }

                Platform.runLater(() -> {
                    gradleTasksCache.put(gradleProjectPath, tasks);
                    Railroad.LOGGER.debug("Cached {} Gradle tasks for {}", tasks != null ? tasks.size() : 0, gradleProjectPath);
                });
            });
    }

    private static Path normalizeGradleProjectPath(String rawValue) {
        if (rawValue == null || rawValue.isBlank())
            return null;

        try {
            return Path.of(rawValue.trim()).toAbsolutePath().normalize();
        } catch (Exception exception) {
            return null;
        }
    }

    @Override
    public void applyConfigurationFormData(FormData formData) {
        applyBaseFormData(formData);
        this.task = formData.get("task", String.class);
        this.gradleProjectPath = Path.of(formData.get("gradleProjectPath", String.class));
        this.environmentVariables = StringUtils.stringToEnvironmentVariables(formData.get("environmentVariables", String.class));
        this.vmOptions = StringUtils.stringToStringArray(formData.get("vmOptions", String.class), " ");
        this.javaHome = formData.get("javaHome", JDK.class);
    }
}
