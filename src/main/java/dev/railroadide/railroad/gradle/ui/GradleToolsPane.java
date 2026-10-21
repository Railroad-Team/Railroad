package dev.railroadide.railroad.gradle.ui;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.command.CommandButtons;
import dev.railroadide.railroad.command.CommandContext;
import dev.railroadide.railroad.command.GradleCommands;
import dev.railroadide.railroad.gradle.GradleSettings;
import dev.railroadide.railroad.gradle.model.GradleBuildModel;
import dev.railroadide.railroad.gradle.model.GradleModelListener;
import dev.railroadide.railroad.gradle.project.GradleManager;
import dev.railroadide.railroad.gradle.service.GradleModelService;
import dev.railroadide.railroad.gradle.ui.deps.GradleDependenciesPane;
import dev.railroadide.railroad.gradle.ui.task.GradleTasksPane;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRToggleButton;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.ui.localized.LocalizedTab;
import dev.railroadide.railroad.ui.localized.LocalizedTooltip;
import dev.railroadide.railroad.ui.styling.ButtonSize;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.StackedFontIcon;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A pane that provides tools for interacting with Gradle, including task execution and dependency management.
 */
@Getter
public class GradleToolsPane extends RRVBox {
    private final GradleManager gradleManager;
    private ButtonBase downloadSourcesButton;
    private ButtonBase toggleOfflineButton;
    private final TabPane tabPane;
    private final Tab tasksTab;
    private final Tab dependenciesTab;

    /**
     * Constructs a new GradleToolsPane for the specified project.
     *
     * @param project the project for which to create the Gradle tools pane
     */
    public GradleToolsPane(Project project) {
        super();
        getStyleClass().add("gradle-tools-pane");

        this.gradleManager = project.getGradleManager();
        GradleModelService modelService = gradleManager.getGradleModelService();

        ButtonBase syncButton = createButtonBarButton(
            FontAwesomeSolid.SYNC,
            "railroad.gradle.tools.button.sync.tooltip",
            "sync-button",
            false);
        CommandButtons.bind(syncButton, GradleCommands.SYNC,
            () -> CommandContext.withArgument(project, this, gradleManager));

        downloadSourcesButton = createButtonBarButton(
            FontAwesomeSolid.DOWNLOAD,
            "railroad.gradle.tools.button.downloadsources.tooltip",
            "download-sources-button",
            false);
        CommandButtons.bind(downloadSourcesButton, GradleCommands.DOWNLOAD_SOURCES,
            () -> CommandContext.withArgument(project, this, this));

        var offlineIcon = new StackedFontIcon();
        offlineIcon.setIconCodes(FontAwesomeSolid.WIFI, FontAwesomeSolid.SLASH);
        toggleOfflineButton = createButtonBarButton(
            offlineIcon,
            "railroad.gradle.tools.button.toggleoffline.tooltip",
            "toggle-offline-button",
            true);
        CommandButtons.bind(toggleOfflineButton, GradleCommands.TOGGLE_OFFLINE,
            () -> CommandContext.withArgument(project, this, this));

        var modelListener = new GradleModelListener() {
            private void setButtonsDisabled(boolean disabled) {
                Platform.runLater(() -> {
                    syncButton.setDisable(disabled);
                    downloadSourcesButton.setDisable(disabled);
                    toggleOfflineButton.setDisable(disabled);
                });
            }

            @Override
            public void modelReloadStarted() {
                setButtonsDisabled(true);
            }

            @Override
            public void modelReloadSucceeded(GradleBuildModel model) {
                setButtonsDisabled(false);
            }

            @Override
            public void modelReloadFailed(Throwable error) {
                setButtonsDisabled(false);
            }
        };
        var modelListenerRegistered = new AtomicBoolean(true);
        modelService.addListener(modelListener);
        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null && modelListenerRegistered.compareAndSet(true, false)) {
                modelService.removeListener(modelListener);
            } else if (newScene != null && modelListenerRegistered.compareAndSet(false, true)) {
                modelService.addListener(modelListener);
            }
        });

        var buttonBar = new RRHBox(2, syncButton, downloadSourcesButton, toggleOfflineButton);
        buttonBar.getStyleClass().add("gradle-tools-buttonbar");

        getChildren().add(buttonBar);

        this.tasksTab = new LocalizedTab("railroad.gradle.tools.tasks", new GradleTasksPane(project));
        this.dependenciesTab = new LocalizedTab("railroad.gradle.tools.dependencies",
            new GradleDependenciesPane(project));

        this.tabPane = new TabPane(tasksTab, dependenciesTab);
        tabPane.getStyleClass().add("gradle-tools-tabpane");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        getChildren().add(tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        modelService.refreshModel(false);

        Services.UI_MANAGER.assignWhileAttached(UIIds.Gradle.GRADLE_TOOLS, this);
    }

    private static ButtonBase createButtonBarButton(Ikon ikon, String tooltipKey, String styleClass, boolean toggle) {
        return toggle
            ? createToggleButtonBarButton(ikon, tooltipKey, styleClass)
            : createButtonBarButton(ikon, tooltipKey, styleClass);
    }

    private static ButtonBase createButtonBarButton(Node ikon, String tooltipKey, String styleClass, boolean toggle) {
        return toggle
            ? createToggleButtonBarButton(ikon, tooltipKey, styleClass)
            : createButtonBarButton(ikon, tooltipKey, styleClass);
    }

    private static RRToggleButton createToggleButtonBarButton(Node graphic, String tooltipKey, String styleClass) {
        var button = new RRToggleButton("", graphic);
        button.setSquare(true);
        button.setButtonSize(ButtonSize.SMALL);
        button.setVariant(ButtonVariant.GHOST);
        button.setTooltip(new LocalizedTooltip(tooltipKey));
        button.getStyleClass().addAll("gradle-tools-buttonbar-button", styleClass);
        return button;
    }

    private static RRToggleButton createToggleButtonBarButton(Ikon graphic, String tooltipKey, String styleClass) {
        var button = new RRToggleButton("", graphic);
        button.setSquare(true);
        button.setButtonSize(ButtonSize.SMALL);
        button.setVariant(ButtonVariant.GHOST);
        button.setTooltip(new LocalizedTooltip(tooltipKey));
        button.getStyleClass().addAll("gradle-tools-buttonbar-button", styleClass);
        return button;
    }

    private static RRButton createButtonBarButton(Node graphic, String tooltipKey, String styleClass) {
        var button = new RRButton("", graphic);
        button.setSquare(true);
        button.setButtonSize(ButtonSize.SMALL);
        button.setVariant(ButtonVariant.GHOST);
        button.setTooltip(new LocalizedTooltip(tooltipKey));
        button.getStyleClass().addAll("gradle-tools-buttonbar-button", styleClass);
        return button;
    }

    private static RRButton createButtonBarButton(Ikon graphic, String tooltipKey, String styleClass) {
        var button = new RRButton("", graphic);
        button.setSquare(true);
        button.setButtonSize(ButtonSize.SMALL);
        button.setVariant(ButtonVariant.GHOST);
        button.setTooltip(new LocalizedTooltip(tooltipKey));
        button.getStyleClass().addAll("gradle-tools-buttonbar-button", styleClass);
        return button;
    }

    /**
     * Checks if the tasks tab is currently selected.
     *
     * @return true if the tasks tab is selected, false otherwise
     */
    public boolean isTasksTabSelected() {
        return this.tabPane.getSelectionModel().getSelectedItem() == tasksTab;
    }

    /**
     * Checks if the dependencies tab is currently selected.
     *
     * @return true if the dependencies tab is selected, false otherwise
     */
    public boolean isDependenciesTabSelected() {
        return this.tabPane.getSelectionModel().getSelectedItem() == dependenciesTab;
    }

    /**
     * Runs the existing download sources operation.
     */
    public void downloadSources() {
        Railroad.LOGGER.info("Downloading Gradle sources...");
        downloadSourcesButton.setDisable(true);
        gradleManager.downloadAllSources().whenComplete((_, throwable) -> {
            if (throwable != null) {
                Railroad.LOGGER.error("Failed to download Gradle sources", throwable);
            } else {
                Railroad.LOGGER.info("Gradle sources downloaded successfully");
            }

            Platform.runLater(() -> downloadSourcesButton.setDisable(false));
        });
    }

    /**
     * Runs the existing toggle offline operation.
     */
    public void toggleOffline() {
        GradleSettings gradleSettings = gradleManager.getGradleSettings();
        boolean newOfflineMode = !gradleSettings.isOfflineMode();
        gradleSettings.setOfflineMode(newOfflineMode);
        gradleManager.saveSettings();
        ((RRToggleButton) toggleOfflineButton).setSelected(newOfflineMode);
    }

    /**
     * Checks whether the source-download control is available.
     *
     * @return whether downloading can be started
     */
    public boolean canDownloadSources() {
        return !downloadSourcesButton.isDisable();
    }
}
