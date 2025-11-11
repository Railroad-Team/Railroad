package dev.railroadide.railroad.ide.ui.setup;

import dev.railroadide.core.localization.LocalizationService;
import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.localized.LocalizedComboBox;
import dev.railroadide.core.ui.localized.LocalizedTooltip;
import dev.railroadide.core.utility.ServiceLocator;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.IDESetup;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.ui.RunConfigurationContextMenuManager;
import dev.railroadide.railroad.ide.runconfig.ui.RunConfigurationListCell;
import dev.railroadide.railroad.project.Project;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Encapsulates the run/debug/stop controls shown in the IDE toolbar.
 * Handles synchronization with the project's run configuration manager and
 * manages tracking of currently running configurations to support multiple instances.
 */
public final class RunControlsPane extends RRHBox {
    private final Project project;
    private final LocalizedComboBox<RunConfiguration<?>> runConfigurationsComboBox;
    private final RRButton runButton = new RRButton("", FontAwesomeSolid.PLAY);
    private final RRButton debugButton = new RRButton("", FontAwesomeSolid.BUG);
    private final RRButton stopButton = new RRButton("", FontAwesomeSolid.STOP);
    private final RRButton moreActionsButton = new RRButton("", FontAwesomeSolid.ELLIPSIS_V);
    private final Map<UUID, Integer> runningConfigurations = new HashMap<>();
    private final Set<UUID> stoppingConfigurations = new HashSet<>();
    private final LocalizedTooltip runButtonTooltip = new LocalizedTooltip("railroad.ide.toolbar.run.tooltip");
    private final LocalizedTooltip restartButtonTooltip = new LocalizedTooltip("railroad.ide.toolbar.restart.tooltip");
    private final LocalizedTooltip debugButtonTooltip = new LocalizedTooltip("railroad.ide.toolbar.debug.tooltip");
    private final LocalizedTooltip debugRestartTooltip = new LocalizedTooltip("railroad.ide.toolbar.debug.restart.tooltip");

    public static Node create(Project project) {
        return new RunControlsPane(project);
    }

    private RunControlsPane(Project project) {
        super(4);
        this.project = project;
        setAlignment(Pos.CENTER_LEFT);

        runConfigurationsComboBox = createRunConfigurationsComboBox();
        configureButtons();

        getChildren().addAll(
            runConfigurationsComboBox,
            runButton,
            debugButton,
            stopButton,
            moreActionsButton
        );
    }

    private LocalizedComboBox<RunConfiguration<?>> createRunConfigurationsComboBox() {
        var comboBox = new LocalizedComboBox<RunConfiguration<?>>(object -> {
            if (object == null)
                return "railroad.ide.toolbar.edit_run_configurations";

            return object.uuid().toString();
        }, string -> {
            if (string == null || string.isEmpty() ||
                "railroad.ide.toolbar.edit_run_configurations".equalsIgnoreCase(string))
                return null;

            try {
                var uuid = UUID.fromString(string);
                return project.getRunConfigManager().getConfigurationByUUID(uuid);
            } catch (IllegalArgumentException exception) {
                Railroad.LOGGER.warn("Failed to parse UUID from string: {}", string, exception);
                return null;
            }
        });

        comboBox.getItems().setAll(project.getRunConfigManager().getConfigurations());
        comboBox.getItems().add(null);

        project.getRunConfigManager().getConfigurations().addListener(
            (ListChangeListener<? super RunConfiguration<?>>) change -> {
                RunConfiguration<?> selected = comboBox.getValue();
                UUID selectedUuid = selected != null ? selected.uuid() : null;

                var configurations = project.getRunConfigManager().getConfigurations();
                comboBox.getItems().setAll(configurations);
                comboBox.getItems().add(null); // For "Edit Run Configurations" option

                if (selectedUuid != null) {
                    RunConfiguration<?> updatedSelection = project.getRunConfigManager().getConfigurationByUUID(selectedUuid);
                    if (updatedSelection != null) {
                        comboBox.setValue(updatedSelection);
                        return;
                    }
                }

                comboBox.getSelectionModel().selectFirst();
            });

        var localizationService = ServiceLocator.getService(LocalizationService.class);
        comboBox.getStyleClass().add("run-config-combobox");
        comboBox.setTooltip(new LocalizedTooltip("railroad.ide.toolbar.run_configurations.tooltip"));
        comboBox.setPrefWidth(200);
        comboBox.setCellFactory(param -> new RunConfigurationListCell(project));
        comboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(RunConfiguration<?> item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                    return;
                }

                if (item == null) {
                    if (project.getRunConfigManager().getConfigurations().isEmpty()) {
                        setText(localizationService.get("railroad.ide.toolbar.no_run_configurations"));
                    } else {
                        setText(localizationService.get("railroad.ide.toolbar.edit_run_configurations"));
                    }

                    return;
                }

                setText(item.data().getName());
            }
        });

        return comboBox;
    }

    private void configureButtons() {
        runButton.setSquare(true);
        runButton.setButtonSize(RRButton.ButtonSize.SMALL);
        runButton.setVariant(RRButton.ButtonVariant.GHOST);
        runButton.setTooltip(runButtonTooltip);
        runButton.getStyleClass().addAll("toolbar-button", "run-button");
        runButton.setFocusTraversable(false);
        runButton.setDisable(true);
        runButton.setOnAction(event -> {
            RunConfiguration<?> selected = runConfigurationsComboBox.getValue();
            if (selected == null || isConfigurationStopping(selected))
                return;

            if (isConfigurationRunning(selected) && !allowsMultipleInstances(selected)) {
                restartConfiguration(selected, false);
            } else {
                startConfigurationExecution(false);
            }
        });

        debugButton.setSquare(true);
        debugButton.setButtonSize(RRButton.ButtonSize.SMALL);
        debugButton.setVariant(RRButton.ButtonVariant.GHOST);
        debugButton.setTooltip(debugButtonTooltip);
        debugButton.getStyleClass().addAll("toolbar-button", "debug-button");
        debugButton.setFocusTraversable(false);
        debugButton.setDisable(true);
        debugButton.setOnAction(event -> {
            RunConfiguration<?> selected = runConfigurationsComboBox.getValue();
            if (selected == null || isConfigurationStopping(selected) || !selected.isDebuggingSupported(project))
                return;

            if (isConfigurationRunning(selected) && !allowsMultipleInstances(selected)) {
                restartConfiguration(selected, true);
            } else {
                startConfigurationExecution(true);
            }
        });

        stopButton.setSquare(true);
        stopButton.setButtonSize(RRButton.ButtonSize.SMALL);
        stopButton.setVariant(RRButton.ButtonVariant.GHOST);
        stopButton.setTooltip(new LocalizedTooltip("railroad.ide.toolbar.stop.tooltip"));
        stopButton.getStyleClass().addAll("toolbar-button", "stop-button");
        stopButton.setFocusTraversable(false);
        stopButton.setDisable(true);
        stopButton.setVisible(false);
        stopButton.managedProperty().bind(stopButton.visibleProperty());
        stopButton.setOnAction(event -> {
            var runningConfigs = getRunningConfigurations();
            if (runningConfigs.size() > 1) {
                showStopMenu(runningConfigs);
            } else {
                stopActiveConfiguration();
            }
        });

        moreActionsButton.setSquare(true);
        moreActionsButton.setButtonSize(RRButton.ButtonSize.SMALL);
        moreActionsButton.setVariant(RRButton.ButtonVariant.GHOST);
        moreActionsButton.setTooltip(new LocalizedTooltip("railroad.ide.toolbar.run_configurations.more_actions.tooltip"));
        moreActionsButton.getStyleClass().addAll("toolbar-button", "more-actions-button");
        moreActionsButton.setFocusTraversable(false);
        moreActionsButton.setOnAction(event -> {
            RunConfiguration<?> item = runConfigurationsComboBox.getValue();
            if (item == null) {
                IDESetup.showEditRunConfigurationsWindow(project, null);
                return;
            }

            var menu = item.createContextMenu(project);
            RunConfigurationContextMenuManager.show(moreActionsButton, menu, Side.BOTTOM);
        });

        runConfigurationsComboBox.valueProperty().addListener((observable, oldValue, newValue) -> updateRunControls());
        runConfigurationsComboBox.getSelectionModel().selectFirst();
        updateRunControls();
    }

    private void startConfigurationExecution(boolean debug) {
        startConfigurationExecution(runConfigurationsComboBox.getValue(), debug);
    }

    private void startConfigurationExecution(@Nullable RunConfiguration<?> configuration, boolean debug) {
        if (configuration == null)
            return;

        if (!allowsMultipleInstances(configuration) && isConfigurationRunning(configuration))
            return;

        incrementRunningConfiguration(configuration);
        updateRunControls();

        var execution = debug ?
            configuration.debug(project) :
            configuration.run(project);

        execution.whenComplete((ignored, throwable) -> Platform.runLater(() -> {
            if (throwable != null) {
                Railroad.LOGGER.error("Run configuration '{}' failed", configuration.data().getName(), throwable);
            }

            decrementRunningConfiguration(configuration);
            updateRunControls();
        }));
    }

    private void stopActiveConfiguration() {
        RunConfiguration<?> active = runConfigurationsComboBox.getValue();
        stopConfiguration(active);
    }

    private CompletableFuture<Void> stopConfiguration(@Nullable RunConfiguration<?> configuration) {
        var future = new CompletableFuture<Void>();
        if (configuration == null) {
            future.complete(null);
            return future;
        }

        boolean isSelectedConfig = Objects.equals(configuration, runConfigurationsComboBox.getValue());
        if (!isConfigurationRunning(configuration)) {
            if (isSelectedConfig) {
                stopButton.setDisable(true);
            }

            future.complete(null);
            return future;
        }

        if (isSelectedConfig) {
            stopButton.setDisable(true);
        }

        markStoppingConfiguration(configuration);
        CompletableFuture<Void> stopOperation;
        try {
            stopOperation = configuration.stop(project);
        } catch (Throwable throwable) {
            Railroad.LOGGER.warn("Failed to stop run configuration {}", configuration.data().getName(), throwable);
            unmarkStoppingConfiguration(configuration);
            updateRunControls();
            future.completeExceptionally(throwable);
            return future;
        }

        stopOperation.whenComplete((ignored, throwable) -> Platform.runLater(() -> {
            if (throwable != null) {
                Railroad.LOGGER.warn("Failed to stop run configuration {}", configuration.data().getName(), throwable);
            }

            unmarkStoppingConfiguration(configuration);
            updateRunControls();
            if (throwable != null) {
                future.completeExceptionally(throwable);
            } else {
                future.complete(null);
            }
        }));

        return future;
    }

    private void restartConfiguration(RunConfiguration<?> configuration, boolean debug) {
        stopConfiguration(configuration).thenAccept(ignored ->
            startConfigurationExecution(configuration, debug));
    }

    private void updateRunControls() {
        RunConfiguration<?> selected = runConfigurationsComboBox.getValue();
        boolean hasSelection = selected != null;
        boolean isRunning = hasSelection && isConfigurationRunning(selected);
        boolean isStopping = hasSelection && isConfigurationStopping(selected);
        boolean allowsParallel = hasSelection && allowsMultipleInstances(selected);
        boolean debugSupported = hasSelection && selected.isDebuggingSupported(project);
        boolean canRestart = hasSelection && isRunning && !allowsParallel && !isStopping;
        boolean canStartNew = hasSelection && (!isRunning || allowsParallel) && !isStopping;
        boolean canDebugRestart = debugSupported && isRunning && !allowsParallel && !isStopping;
        boolean canStartDebug = debugSupported && (!isRunning || allowsParallel) && !isStopping;

        runButton.setDisable(!(canRestart || canStartNew));
        runButton.setIcon(canRestart ? FontAwesomeSolid.FORWARD : FontAwesomeSolid.PLAY);
        runButton.setTooltip(canRestart ? restartButtonTooltip : runButtonTooltip);
        debugButton.setDisable(!(canDebugRestart || canStartDebug));
        debugButton.setTooltip(canDebugRestart ? debugRestartTooltip : debugButtonTooltip);
        stopButton.setDisable(!isRunning || isStopping);
        stopButton.setVisible(isRunning);
    }

    private void incrementRunningConfiguration(RunConfiguration<?> configuration) {
        runningConfigurations.merge(configuration.uuid(), 1, Integer::sum);
    }

    private void decrementRunningConfiguration(RunConfiguration<?> configuration) {
        runningConfigurations.computeIfPresent(configuration.uuid(), (uuid, count) -> count > 1 ? count - 1 : null);
        unmarkStoppingConfiguration(configuration);
    }

    private boolean isConfigurationRunning(@Nullable RunConfiguration<?> configuration) {
        return configuration != null && runningConfigurations.getOrDefault(configuration.uuid(), 0) > 0;
    }

    private boolean isConfigurationStopping(@Nullable RunConfiguration<?> configuration) {
        return configuration != null && stoppingConfigurations.contains(configuration.uuid());
    }

    private void markStoppingConfiguration(@Nullable RunConfiguration<?> configuration) {
        if (configuration == null)
            return;

        stoppingConfigurations.add(configuration.uuid());
    }

    private void unmarkStoppingConfiguration(@Nullable RunConfiguration<?> configuration) {
        if (configuration == null)
            return;

        stoppingConfigurations.remove(configuration.uuid());
    }

    private static boolean allowsMultipleInstances(@Nullable RunConfiguration<?> configuration) {
        return configuration != null && configuration.data().isAllowMultipleInstances();
    }

    private List<RunConfiguration<?>> getRunningConfigurations() {
        var manager = project.getRunConfigManager();
        var configs = new ArrayList<RunConfiguration<?>>();
        for (UUID uuid : runningConfigurations.keySet()) {
            RunConfiguration<?> configuration = manager.getConfigurationByUUID(uuid);
            if (configuration != null) {
                configs.add(configuration);
            }
        }

        return configs;
    }

    private void showStopMenu(List<RunConfiguration<?>> runningConfigs) {
        var localizationService = ServiceLocator.getService(LocalizationService.class);
        var menu = new ContextMenu();

        for (RunConfiguration<?> configuration : runningConfigs) {
            int instanceCount = runningConfigurations.getOrDefault(configuration.uuid(), 0);
            String label = configuration.data().getName();
            if (instanceCount > 1) {
                label += " (" + instanceCount + ")";
            }

            var item = new MenuItem(label);
            item.setOnAction(event -> {
                runConfigurationsComboBox.setValue(configuration);
                stopConfiguration(configuration);
            });
            menu.getItems().add(item);
        }

        if (!menu.getItems().isEmpty()) {
            menu.getItems().add(new SeparatorMenuItem());
        }

        var stopAllItem = new MenuItem(localizationService.get("railroad.ide.toolbar.stop.all"));
        stopAllItem.setOnAction(event -> {
            for (RunConfiguration<?> configuration : runningConfigs) {
                stopConfiguration(configuration);
            }
        });
        menu.getItems().add(stopAllItem);

        menu.show(stopButton, Side.BOTTOM, 0, 0);
    }
}
