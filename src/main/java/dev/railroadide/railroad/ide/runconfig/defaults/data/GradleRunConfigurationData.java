package dev.railroadide.railroad.ide.runconfig.defaults.data;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.form.*;
import dev.railroadide.railroad.gradle.model.GradleBuildModel;
import dev.railroadide.railroad.gradle.service.GradleModelService;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationData;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationType;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationTypes;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.JDKManager;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.onboarding.ProjectValidators;
import dev.railroadide.railroad.settings.ui.DetectedJdkListPane;
import dev.railroadide.railroad.utility.StringUtils;
import dev.railroadide.railroadplugin.dto.RailroadGradleTask;
import dev.railroadide.railroadplugin.dto.RailroadGradleTaskArgument;
import dev.railroadide.railroadplugin.dto.RailroadModule;
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
import org.gradle.tooling.model.DomainObjectSet;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
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

        ObservableMap<Path, List<? extends RailroadGradleTask>> gradleTasksCache = FXCollections.observableHashMap();
        ObjectProperty<Path> gradleProjectPathProperty = new SimpleObjectProperty<>(this.gradleProjectPath);
        gradleProjectPathProperty.addListener((observable, oldValue, newValue) ->
            loadGradleTasksAsync(project, newValue, gradleTasksCache));
        loadGradleTasksAsync(project, gradleProjectPathProperty.get(), gradleTasksCache);

        return createBaseFormBuilder(project, configuration)
            .appendSection(FormSection.create("railroad.runconfig.gradle.configuration.title")
                .appendComponent(FormComponent.textField("task", "railroad.runconfig.gradle.configuration.task.label")
                    .required()
                    .text(() -> this.task != null ? this.task : "")
                    .promptText("railroad.runconfig.gradle.configuration.task.prompt")
                    .autoComplete(query ->
                        filterGradleTaskSuggestions(query, gradleProjectPathProperty.get(), gradleTasksCache))
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

    private List<String> buildGradleTaskSuggestions(Path gradleProjectPath, ObservableMap<Path, List<? extends RailroadGradleTask>> gradleTasksCache) {
        if (gradleProjectPath == null)
            return List.of();

        List<? extends RailroadGradleTask> cachedTasks = gradleTasksCache.get(gradleProjectPath);
        if (cachedTasks == null || cachedTasks.isEmpty()) {
            Railroad.LOGGER.debug("No cached Gradle tasks for {} yet", gradleProjectPath);
            return List.of();
        }

        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        for (RailroadGradleTask task : cachedTasks) {
            if (task == null)
                continue;

            String taskName = task.getName();
            if (taskName != null && !taskName.isBlank())
                suggestions.add(taskName);

            DomainObjectSet<? extends RailroadGradleTaskArgument> options = task.getArguments();
            if (options != null && !options.isEmpty()) {
                options.stream()
                    .map(RailroadGradleTaskArgument::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .forEach(suggestions::add);
            }
        }

        List<String> result = List.copyOf(suggestions);
        Railroad.LOGGER.debug("Providing {} suggestions for {}", result.size(), gradleProjectPath);
        return result;
    }

    private Collection<String> filterGradleTaskSuggestions(String query, Path gradleProjectPath,
                                                           ObservableMap<Path, List<? extends RailroadGradleTask>> gradleTasksCache) {
        List<String> suggestions = buildGradleTaskSuggestions(gradleProjectPath, gradleTasksCache);
        String token = currentToken(query);
        if (token.isBlank()) {
            return suggestions;
        }

        String normalized = token.toLowerCase(Locale.ROOT);
        return suggestions.stream()
            .filter(Objects::nonNull)
            .filter(s -> s.toLowerCase(Locale.ROOT).contains(normalized))
            .toList();
    }

    private String currentToken(String text) {
        if (text == null || text.isBlank())
            return "";

        int lastSpace = Math.max(text.lastIndexOf(' '), text.lastIndexOf('\t'));
        if (lastSpace == -1)
            return text.trim();

        if (lastSpace == text.length() - 1)
            return "";

        return text.substring(lastSpace + 1).trim();
    }

    private Callback<ListView<String>, ListCell<String>> createGradleTaskSuggestionCellFactory(
        ObjectProperty<Path> gradleProjectPathProperty,
        ObservableMap<Path, List<? extends RailroadGradleTask>> gradleTasksCache) {
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
                                                       ObservableMap<Path, List<? extends RailroadGradleTask>> gradleTasksCache) {
        if (taskOrOptionName == null || gradleProjectPath == null)
            return null;

        List<? extends RailroadGradleTask> tasks = gradleTasksCache.get(gradleProjectPath);
        if (tasks == null || tasks.isEmpty())
            return null;

        for (RailroadGradleTask task : tasks) {
            if (task == null)
                continue;

            if (taskOrOptionName.equals(task.getName())) {
                String description = task.getDescription();
                return description == null || description.isBlank() ? null : description;
            }

            DomainObjectSet<? extends RailroadGradleTaskArgument> arguments = task.getArguments();
            if (arguments != null && !arguments.isEmpty()) {
                for (RailroadGradleTaskArgument argument : arguments) {
                    if (argument == null)
                        continue;

                    if (taskOrOptionName.equals(argument.getName())) {
                        String description = argument.getDescription();
                        if (description != null && !description.isBlank())
                            return description;
                    }
                }
            }
        }

        return null;
    }

    private void loadGradleTasksAsync(Project project, Path gradleProjectPath, ObservableMap<Path, List<? extends RailroadGradleTask>> gradleTasksCache) {
        if (gradleProjectPath == null) {
            if (Platform.isFxApplicationThread()) {
                gradleTasksCache.clear();
            } else {
                Platform.runLater(gradleTasksCache::clear);
            }

            return;
        }

        Railroad.LOGGER.debug("Loading Gradle tasks for {}", gradleProjectPath);
        CompletableFuture.supplyAsync(() -> fetchTasksForProject(project, gradleProjectPath))
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

    private List<? extends RailroadGradleTask> fetchTasksForProject(Project project, Path gradleProjectPath) {
        try {
            GradleModelService modelService = project.getGradleManager().getGradleModelService();
            GradleBuildModel model = modelService.refreshModel(false).get();
            if (model == null)
                return List.of();

            for (RailroadModule module : model.project().getModules()) {
                if (module == null)
                    continue;

                if (gradleProjectPath.equals(module.getGradleProject().getProjectDirectory().toPath())) {
                    DomainObjectSet<? extends RailroadGradleTask> tasks = module.getTasks();
                    return tasks != null ? tasks.stream().toList() : List.of();
                }
            }

            return model.project().getModules().stream()
                .flatMap(module -> module.getTasks().stream())
                .filter(Objects::nonNull)
                .findFirst()
                .map(List::of)
                .orElse(List.of());
        } catch (Exception exception) {
            Railroad.LOGGER.warn("Failed to fetch Gradle tasks for {}", gradleProjectPath, exception);
            return List.of();
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
