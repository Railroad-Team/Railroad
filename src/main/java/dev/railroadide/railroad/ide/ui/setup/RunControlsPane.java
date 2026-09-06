package dev.railroadide.railroad.ide.ui.setup;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.command.*;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.ui.RunConfigurationContextMenuManager;
import dev.railroadide.railroad.ide.runconfig.ui.RunConfigurationListCell;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.ui.localized.LocalizedComboBox;
import dev.railroadide.railroad.ui.localized.LocalizedMenuItem;
import dev.railroadide.railroad.ui.localized.LocalizedTooltip;
import dev.railroadide.railroad.ui.styling.ButtonSize;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
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
    private final LocalizedTooltip debugRestartTooltip = new LocalizedTooltip(
        "railroad.ide.toolbar.debug.restart.tooltip");

    /**
     * Creates project run-configuration selection and execution controls.
     *
     * @param project project whose files and workspace are being displayed
     * @return run controls node
     */
    public static Node create(Project project) {
        return new RunControlsPane(project);
    }

    private RunControlsPane(Project project) {
        super(4);
        this.project = project;
        setAlignment(Pos.CENTER_LEFT);

        runConfigurationsComboBox = createRunConfigurationsComboBox();
        configureButtons();
        Services.UI_MANAGER.assignWhileAttached(UIIds.IDE.RUN_CONTROLS, this);

        getChildren().addAll(
            runConfigurationsComboBox,
            runButton,
            debugButton,
            stopButton,
            moreActionsButton);
    }

    private LocalizedComboBox<RunConfiguration<?>> createRunConfigurationsComboBox() {
        var comboBox = new LocalizedComboBox<RunConfiguration<?>>(object -> {
            if (object == null)
                return "railroad.ide.toolbar.edit_run_configurations";

            return object.uuid().toString();
        });

        comboBox.getItems().setAll(project.getRunConfigManager().getConfigurations());
        comboBox.getItems().add(null);

        project.getRunConfigManager().getConfigurations().addListener(
            (ListChangeListener<? super RunConfiguration<?>>) _ -> {
                RunConfiguration<?> selected = comboBox.getValue();
                UUID selectedUuid = selected != null ? selected.uuid() : null;

                var configurations = project.getRunConfigManager().getConfigurations();
                comboBox.getItems().setAll(configurations);
                comboBox.getItems().add(null); // For "Edit Run Configurations" option

                if (selectedUuid != null) {
                    RunConfiguration<?> updatedSelection = project.getRunConfigManager()
                        .getConfigurationByUUID(selectedUuid);
                    if (updatedSelection != null) {
                        comboBox.setValue(updatedSelection);
                        return;
                    }
                }

                comboBox.getSelectionModel().selectFirst();
            });

        comboBox.getStyleClass().add("run-config-combobox");
        comboBox.setTooltip(new LocalizedTooltip("railroad.ide.toolbar.run_configurations.tooltip"));
        comboBox.setCellFactory(_ -> new RunConfigurationListCell(project));
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
                        setText(L18n.localize("railroad.ide.toolbar.no_run_configurations"));
                    } else {
                        setText(L18n.localize("railroad.ide.toolbar.edit_run_configurations"));
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
        runButton.setButtonSize(ButtonSize.SMALL);
        runButton.setVariant(ButtonVariant.GHOST);
        runButton.setTooltip(runButtonTooltip);
        runButton.getStyleClass().addAll("toolbar-button", "run-button");
        runButton.setFocusTraversable(false);
        runButton.setDisable(true);
        CommandButtons.bind(runButton, RunCommands.RUN, () -> CommandContext.forProject(project, this));

        debugButton.setSquare(true);
        debugButton.setButtonSize(ButtonSize.SMALL);
        debugButton.setVariant(ButtonVariant.GHOST);
        debugButton.setTooltip(debugButtonTooltip);
        debugButton.getStyleClass().addAll("toolbar-button", "debug-button");
        debugButton.setFocusTraversable(false);
        debugButton.setDisable(true);
        CommandButtons.bind(debugButton, RunCommands.DEBUG, () -> CommandContext.forProject(project, this));

        stopButton.setSquare(true);
        stopButton.setButtonSize(ButtonSize.SMALL);
        stopButton.setVariant(ButtonVariant.GHOST);
        stopButton.setTooltip(new LocalizedTooltip("railroad.ide.toolbar.stop.tooltip"));
        stopButton.getStyleClass().addAll("toolbar-button", "stop-button");
        stopButton.setFocusTraversable(false);
        stopButton.setDisable(true);
        stopButton.setVisible(false);
        stopButton.managedProperty().bind(stopButton.visibleProperty());
        CommandButtons.bind(stopButton, RunCommands.STOP, () -> CommandContext.forProject(project, this));

        moreActionsButton.setSquare(true);
        moreActionsButton.setButtonSize(ButtonSize.SMALL);
        moreActionsButton.setVariant(ButtonVariant.GHOST);
        moreActionsButton
            .setTooltip(new LocalizedTooltip("railroad.ide.toolbar.run_configurations.more_actions.tooltip"));
        moreActionsButton.getStyleClass().addAll("toolbar-button", "more-actions-button");
        moreActionsButton.setFocusTraversable(false);
        moreActionsButton.setOnAction(_ -> {
            RunConfiguration<?> item = runConfigurationsComboBox.getValue();
            if (item == null) {
                CommandDispatcher.execute(RunCommands.EDIT, CommandContext.forProject(project, this));
                return;
            }

            var menu = item.createContextMenu(project);
            RunConfigurationContextMenuManager.show(moreActionsButton, menu, Side.BOTTOM);
        });

        runConfigurationsComboBox.valueProperty().addListener((_, _, _) -> updateRunControls());
        runConfigurationsComboBox.getSelectionModel().selectFirst();
        updateRunControls();
    }

    /**
     * Returns the project whose executions these controls track.
     *
     * @return owning project
     */
    public Project getProject() {
        return project;
    }

    /**
     * Returns the toolbar's selected run configuration.
     *
     * @return selected configuration, or null
     */
    public RunConfiguration<?> selectedConfiguration() {
        return runConfigurationsComboBox.getValue();
    }

    /**
     * Checks selection, stopping state, and debug support for a run request.
     *
     * @param configuration configuration to operate on, or null when no selection exists
     * @param debug whether the request uses debugging
     * @return whether the configuration can start or restart
     */
    public boolean canExecuteConfiguration(RunConfiguration<?> configuration, boolean debug) {
        return configuration != null && !isConfigurationStopping(configuration) &&
            (!debug || configuration.isDebuggingSupported(project));
    }

    /**
     * Checks whether a configuration is running and not already stopping.
     *
     * @param configuration configuration to operate on, or null when no selection exists
     * @return whether a stop request can be made
     */
    public boolean canStopConfiguration(RunConfiguration<?> configuration) {
        return isConfigurationRunning(configuration) && !isConfigurationStopping(configuration);
    }

    /**
     * Checks whether any tracked execution can still be stopped.
     *
     * @return whether a stoppable execution exists
     */
    public boolean hasRunningConfigurations() {
        return getRunningConfigurations().stream().anyMatch(this::canStopConfiguration);
    }

    /**
     * Starts or restarts a configuration through the shared execution tracking.
     *
     * @param configuration configuration to operate on, or null when no selection exists
     * @param debug whether the request uses debugging
     */
    public void executeConfiguration(RunConfiguration<?> configuration, boolean debug) {
        if (!canExecuteConfiguration(configuration, debug))
            return;
        runConfigurationsComboBox.setValue(configuration);
        if (isConfigurationRunning(configuration) && !allowsMultipleInstances(configuration)) {
            restartConfiguration(configuration, debug);
        } else {
            startConfigurationExecution(configuration, debug);
        }
    }

    /**
     * Stops the sole running configuration or opens the existing stop-selection menu.
     */
    public void requestStop() {
        var running = getRunningConfigurations();
        if (running.size() > 1) {
            showStopMenu(running);
        } else if (!running.isEmpty()) {
            stopConfiguration(running.getFirst());
        }
    }

    /**
     * Requests stop for every tracked configuration that is not already stopping.
     */
    public void stopAllConfigurations() {
        for (var configuration : getRunningConfigurations()) {
            if (canStopConfiguration(configuration)) {
                stopConfiguration(configuration);
            }
        }
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

        CompletableFuture<?> execution;
        try {
            execution = debug ? configuration.debug(project) : configuration.run(project);
        } catch (Throwable failure) {
            decrementRunningConfiguration(configuration);
            updateRunControls();
            Railroad.LOGGER.error("Unable to start run configuration", failure);
            return;
        }

        execution.whenComplete((_, throwable) -> Platform.runLater(() -> {
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

    /**
     * Requests stop and updates the tracked stopping state.
     *
     * @param configuration configuration to operate on, or null when no selection exists
     * @return future completed when the stop operation finishes
     */
    public CompletableFuture<Void> stopConfiguration(@Nullable RunConfiguration<?> configuration) {
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

        stopOperation.whenComplete((_, throwable) -> Platform.runLater(() -> {
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
        stopConfiguration(configuration).thenAccept(_ -> startConfigurationExecution(configuration, debug));
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
        stopButton.setDisable(!hasRunningConfigurations());
        stopButton.setVisible(!runningConfigurations.isEmpty());
    }

    private void incrementRunningConfiguration(RunConfiguration<?> configuration) {
        runningConfigurations.merge(configuration.uuid(), 1, Integer::sum);
    }

    private void decrementRunningConfiguration(RunConfiguration<?> configuration) {
        runningConfigurations.computeIfPresent(configuration.uuid(), (_, count) -> count > 1 ? count - 1 : null);
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
        var menu = new ContextMenu();

        for (RunConfiguration<?> configuration : runningConfigs) {
            int instanceCount = runningConfigurations.getOrDefault(configuration.uuid(), 0);
            String label = configuration.data().getName();
            if (instanceCount > 1) {
                label += " (" + instanceCount + ")";
            }

            var item = new MenuItem(label);
            CommandMenuItems.bind(item, RunCommands.STOP,
                () -> CommandContext.withArgument(project, this, configuration));
            menu.getItems().add(item);
        }

        if (!menu.getItems().isEmpty()) {
            menu.getItems().add(new SeparatorMenuItem());
        }

        var stopAllItem = new LocalizedMenuItem("railroad.ide.toolbar.stop.all");
        CommandMenuItems.bind(stopAllItem, RunCommands.STOP_ALL,
            () -> CommandContext.forProject(project, this));
        menu.getItems().add(stopAllItem);

        menu.show(stopButton, Side.BOTTOM, 0, 0);
    }
}
