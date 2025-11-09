package dev.railroadide.railroad.ide;

import com.kodedu.terminalfx.Terminal;
import com.kodedu.terminalfx.TerminalBuilder;
import com.kodedu.terminalfx.config.TerminalConfig;
import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.core.localization.LocalizationService;
import dev.railroadide.core.settings.keybinds.KeybindContexts;
import dev.railroadide.core.settings.keybinds.KeybindData;
import dev.railroadide.core.ui.*;
import dev.railroadide.core.ui.localized.*;
import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.core.utility.ServiceLocator;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.projectexplorer.ProjectExplorerPane;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.ui.RunConfigurationContextMenuManager;
import dev.railroadide.railroad.ide.runconfig.ui.RunConfigurationEditorPane;
import dev.railroadide.railroad.ide.runconfig.ui.RunConfigurationListCell;
import dev.railroadide.railroad.ide.ui.ConsolePane;
import dev.railroadide.railroad.ide.ui.IDEWelcomePane;
import dev.railroadide.railroad.ide.ui.ImageViewerPane;
import dev.railroadide.railroad.ide.ui.StatusBarPane;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.settings.keybinds.KeybindHandler;
import dev.railroadide.railroad.settings.ui.SettingsPane;
import dev.railroadide.railroad.window.WindowBuilder;
import dev.railroadide.railroad.window.WindowManager;
import dev.railroadide.railroadpluginapi.events.ProjectEvent;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class IDESetup {
    private static boolean isSwitchingToIDE = false;

    /**
     * Create a new IDE window for the given project.
     *
     * @param project The project to create the IDE window for
     * @return The created IDE window
     */
    public static Scene createIDEScene(Project project) {
        var root = new RRBorderPane();
        var topBar = new RRHBox(createMenuBar(), new Region(), createToolbar(project));
        HBox.setHgrow(topBar.getChildren().get(1), Priority.ALWAYS);
        root.setTop(topBar);

        var leftPane = new DetachableTabPane();
        leftPane.addTab("Project", new ProjectExplorerPane(project, root));

        var rightPane = new DetachableTabPane();
        rightPane.addTab("Properties", createNotImplementedPane());

        var editorPane = new DetachableTabPane();
        editorPane.addTab("Welcome", new IDEWelcomePane());

        var consolePane = new DetachableTabPane();
        consolePane.addTab("Console", new ConsolePane());
        consolePane.addTab("Terminal", createTerminal(project.getPath()));

        var centerBottomSplit = new SplitPane(editorPane, consolePane);
        centerBottomSplit.setOrientation(Orientation.VERTICAL);
        centerBottomSplit.setDividerPositions(0.75);

        var mainSplit = new SplitPane(leftPane, centerBottomSplit, rightPane);
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        mainSplit.setDividerPositions(0.15, 0.85);
        root.setCenter(mainSplit);

        root.setLeft(buildPaneIconBar(
            leftPane,
            mainSplit,
            Orientation.VERTICAL,
            0,
            Map.of("Project", FontAwesomeSolid.FOLDER.getDescription())
        ));

        root.setRight(buildPaneIconBar(
            rightPane,
            mainSplit,
            Orientation.VERTICAL,
            2,
            Map.of("Properties", FontAwesomeSolid.INFO_CIRCLE.getDescription())
        ));

        var bottomBar = new RRVBox();
        var bottomIcons = buildPaneIconBar(
            consolePane,
            centerBottomSplit,
            Orientation.HORIZONTAL,
            1,
            Map.of(
                "Console", FontAwesomeSolid.PLAY_CIRCLE.getDescription(),
                "Terminal", FontAwesomeSolid.TERMINAL.getDescription()
            )
        );
        bottomBar.getChildren().addAll(
            bottomIcons,
            new StatusBarPane()
        );
        root.setBottom(bottomBar);

        KeybindHandler.registerCapture(KeybindContexts.of("railroad:ide"), root);
        return new Scene(root);
    }

    private static Node createToolbar(Project project) {
        var toolbar = new RRHBox(8);
        toolbar.getStyleClass().add("toolbar");

        var runConfigurationsComboBox = new LocalizedComboBox<RunConfiguration<?>>(object -> {
            if (object == null)
                return "railroad.ide.toolbar.edit_run_configurations";
            return object.uuid().toString();
        }, string -> {
            if (string == null || string.isEmpty() || "railroad.ide.toolbar.edit_run_configurations".equalsIgnoreCase(string))
                return null;

            try {
                var uuid = UUID.fromString(string);
                return project.getRunConfigManager().getConfigurationByUUID(uuid);
            } catch (IllegalArgumentException exception) {
                Railroad.LOGGER.warn("Failed to parse UUID from string: {}", string, exception);
                return null;
            }
        });
        runConfigurationsComboBox.getItems().setAll(project.getRunConfigManager().getConfigurations());
        runConfigurationsComboBox.getItems().add(null);
        project.getRunConfigManager().getConfigurations().addListener(
            (ListChangeListener<? super RunConfiguration<?>>) change -> {
                var selected = runConfigurationsComboBox.getValue();
                runConfigurationsComboBox.getItems().setAll(project.getRunConfigManager().getConfigurations());
                runConfigurationsComboBox.getItems().add(null); // For "Edit Run Configurations" option
                if (selected != null && project.getRunConfigManager().getConfigurations().contains(selected)) {
                    runConfigurationsComboBox.setValue(selected);
                } else {
                    runConfigurationsComboBox.getSelectionModel().selectFirst();
                }
            });

        if (!runConfigurationsComboBox.getItems().isEmpty()) {
            runConfigurationsComboBox.getSelectionModel().selectFirst();
        }

        var localizationService = ServiceLocator.getService(LocalizationService.class);

        runConfigurationsComboBox.getStyleClass().add("run-config-combobox");
        runConfigurationsComboBox.setTooltip(new LocalizedTooltip("railroad.ide.toolbar.run_configurations.tooltip"));
        runConfigurationsComboBox.setPrefWidth(200);
        runConfigurationsComboBox.setCellFactory(
            param -> new RunConfigurationListCell(
                project,
                () -> showEditRunConfigurationsWindow(project, null)));
        runConfigurationsComboBox.setButtonCell(new ListCell<>() {
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

        var runButton = new RRButton("", FontAwesomeSolid.PLAY);
        runButton.setSquare(true);
        runButton.setButtonSize(RRButton.ButtonSize.SMALL);
        runButton.setVariant(RRButton.ButtonVariant.GHOST);
        runButton.setTooltip(new LocalizedTooltip("railroad.ide.toolbar.run.tooltip"));
        runButton.getStyleClass().addAll("toolbar-button", "run-button");
        runButton.setFocusTraversable(false);
        runButton.setDisable(true);
        runButton.setOnAction(event -> {
            RunConfiguration<?> item = runConfigurationsComboBox.getValue();
            if (item == null) {
                runButton.setDisable(true);
                return;
            }

            item.run(project);
            // TODO: Update stop button state to notify that a process is running
        });

        var debugButton = new RRButton("", FontAwesomeSolid.BUG);
        debugButton.setSquare(true);
        debugButton.setButtonSize(RRButton.ButtonSize.SMALL);
        debugButton.setVariant(RRButton.ButtonVariant.GHOST);
        debugButton.setTooltip(new LocalizedTooltip("railroad.ide.toolbar.debug.tooltip"));
        debugButton.getStyleClass().addAll("toolbar-button", "debug-button");
        debugButton.setFocusTraversable(false);
        debugButton.setDisable(true);
        debugButton.setOnAction(event -> {
            RunConfiguration<?> item = runConfigurationsComboBox.getValue();
            if (item == null || !item.isDebuggingSupported(project)) {
                debugButton.setDisable(true);
                return;
            }

            item.debug(project);
            // TODO: Update stop button state to notify that a process is running
        });

        var stopButton = new RRButton("", FontAwesomeSolid.STOP);
        stopButton.setSquare(true);
        stopButton.setButtonSize(RRButton.ButtonSize.SMALL);
        stopButton.setVariant(RRButton.ButtonVariant.GHOST);
        stopButton.setTooltip(new LocalizedTooltip("railroad.ide.toolbar.stop.tooltip"));
        stopButton.getStyleClass().addAll("toolbar-button", "stop-button");
        stopButton.setFocusTraversable(false);
        stopButton.setDisable(true);
        stopButton.setVisible(false);
        stopButton.setOnAction(event -> {
            RunConfiguration<?> item = runConfigurationsComboBox.getValue();
            if (item == null) {
                stopButton.setDisable(true);
                return;
            }

            item.stop(project);
            // TODO: Update button state based on whether a process is running
        });

        var moreActionsButton = new RRButton("", FontAwesomeSolid.ELLIPSIS_V);
        moreActionsButton.setSquare(true);
        moreActionsButton.setButtonSize(RRButton.ButtonSize.SMALL);
        moreActionsButton.setVariant(RRButton.ButtonVariant.GHOST);
        moreActionsButton.setTooltip(new LocalizedTooltip("railroad.ide.toolbar.run_configurations.more_actions.tooltip"));
        moreActionsButton.getStyleClass().addAll("toolbar-button", "more-actions-button");
        moreActionsButton.setFocusTraversable(false);
        moreActionsButton.setOnAction(event -> {
            RunConfiguration<?> item = runConfigurationsComboBox.getValue();
            if (item == null) {
                showEditRunConfigurationsWindow(project, null);
                return;
            }

            var menu = item.createContextMenu(project);
            RunConfigurationContextMenuManager.show(moreActionsButton, menu, Side.BOTTOM);
        });

        RunConfiguration<?> initiallySelected = runConfigurationsComboBox.getValue();
        if (initiallySelected != null) {
            runButton.setDisable(false);
            debugButton.setDisable(!initiallySelected.isDebuggingSupported(project));
        }

        var runSection = new RRHBox(4, runConfigurationsComboBox, runButton, debugButton, moreActionsButton);
        runSection.setAlignment(Pos.CENTER_LEFT);
        toolbar.getChildren().add(runSection);

        return toolbar;
    }

    public static void showEditRunConfigurationsWindow(@NotNull Project project, @Nullable RunConfiguration<?> runConfiguration) {
        var editorPane = new RunConfigurationEditorPane(project);
        WindowBuilder.create()
            .owner(Railroad.WINDOW_MANAGER.getPrimaryStage())
            .title("railroad.window.ide.toolbar.edit_run_configurations", true)
            .applyPreferredSize()
            .scene(new Scene(editorPane))
            .onInit(stage -> editorPane.selectConfiguration(runConfiguration))
            .build();
    }

    /**
     * Switch to the IDE window
     * <p>
     * This method switches the window to the IDE window
     * and sets the current project to the provided project
     * and notifies the plugins of the activity
     *
     * @param project The project to switch to
     */
    public static void switchToIDE(Project project) {
        if (isSwitchingToIDE)
            return; // Prevent multiple simultaneous IDE window creations

        isSwitchingToIDE = true;

        Platform.runLater(() -> {
            try {
                Scene ideScene = IDESetup.createIDEScene(project);
                Stage ideStage = Railroad.WINDOW_MANAGER.getPrimaryStage();
                ideStage.setTitle(Services.APPLICATION_INFO.getName() + " " + Services.APPLICATION_INFO.getVersion() + " - " + project.getAlias());
                ideStage.setScene(ideScene);
                ideStage.setResizable(true);
                ideStage.setMaximized(true);
                Railroad.WINDOW_MANAGER.setPrimaryStage(ideStage);

                try {
                    Railroad.PROJECT_MANAGER.setCurrentProject(project);
                    Railroad.EVENT_BUS.publish(new ProjectEvent(project, ProjectEvent.EventType.OPENED));
                } finally {
                    isSwitchingToIDE = false;
                }
            } catch (Exception exception) {
                isSwitchingToIDE = false;
                throw exception;
            }
        });
    }

    private static MenuBar createMenuBar() {
        var newFileItem = new LocalizedMenuItem("railroad.menu.file.new_file");
        newFileItem.setGraphic(new FontIcon(FontAwesomeSolid.FILE));
        newFileItem.setKeybindData(new KeybindData(KeyCode.N, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN}));

        var openFileItem = new LocalizedMenuItem("railroad.menu.file.open_file");
        openFileItem.setGraphic(new FontIcon(FontAwesomeSolid.FOLDER_OPEN));
        openFileItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));

        var saveItem = new LocalizedMenuItem("railroad.menu.file.save");
        saveItem.setGraphic(new FontIcon(FontAwesomeSolid.SAVE));
        saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));

        var saveAsItem = new LocalizedMenuItem("railroad.menu.file.save_as");
        saveAsItem.setGraphic(new FontIcon(FontAwesomeSolid.SAVE));
        saveAsItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));

        var separator1 = new SeparatorMenuItem();

        var exitItem = new LocalizedMenuItem("railroad.menu.file.exit");
        exitItem.setGraphic(new FontIcon(FontAwesomeSolid.SIGN_OUT_ALT));
        exitItem.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN));
        exitItem.setOnAction(e -> Platform.exit());

        var undoItem = new LocalizedMenuItem("railroad.menu.edit.undo");
        undoItem.setGraphic(new FontIcon(FontAwesomeSolid.UNDO));
        undoItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN));

        var redoItem = new LocalizedMenuItem("railroad.menu.edit.redo");
        redoItem.setGraphic(new FontIcon(FontAwesomeSolid.REDO));
        redoItem.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN));

        var separator2 = new SeparatorMenuItem();

        var cutItem = new LocalizedMenuItem("railroad.menu.edit.cut");
        cutItem.setGraphic(new FontIcon(FontAwesomeSolid.CUT));
        cutItem.setAccelerator(new KeyCodeCombination(KeyCode.X, KeyCombination.SHORTCUT_DOWN));

        var copyItem = new LocalizedMenuItem("railroad.menu.edit.copy");
        copyItem.setGraphic(new FontIcon(FontAwesomeSolid.COPY));
        copyItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN));

        var pasteItem = new LocalizedMenuItem("railroad.menu.edit.paste");
        pasteItem.setGraphic(new FontIcon(FontAwesomeSolid.PASTE));
        pasteItem.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN));

        var separator3 = new SeparatorMenuItem();

        var findItem = new LocalizedMenuItem("railroad.menu.edit.find");
        findItem.setGraphic(new FontIcon(FontAwesomeSolid.SEARCH));
        findItem.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN));

        var replaceItem = new LocalizedMenuItem("railroad.menu.edit.replace");
        replaceItem.setGraphic(new FontIcon(FontAwesomeSolid.SEARCH_PLUS));
        replaceItem.setAccelerator(new KeyCodeCombination(KeyCode.H, KeyCombination.CONTROL_DOWN));

        var projectExplorerItem = new LocalizedCheckMenuItem("railroad.menu.view.project_explorer", true);
        projectExplorerItem.setGraphic(new FontIcon(FontAwesomeSolid.FOLDER));
        projectExplorerItem.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.SHORTCUT_DOWN));

        var propertiesItem = new LocalizedCheckMenuItem("railroad.menu.view.properties", true);
        propertiesItem.setGraphic(new FontIcon(FontAwesomeSolid.INFO_CIRCLE));
        propertiesItem.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.SHORTCUT_DOWN));

        var consoleItem = new LocalizedCheckMenuItem("railroad.menu.view.console", true);
        consoleItem.setGraphic(new FontIcon(FontAwesomeSolid.TERMINAL));
        consoleItem.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT3, KeyCombination.SHORTCUT_DOWN));

        var separator4 = new SeparatorMenuItem();

        var fullScreenItem = new LocalizedMenuItem("railroad.menu.view.full_screen");
        fullScreenItem.setGraphic(new FontIcon(FontAwesomeSolid.EXPAND));
        fullScreenItem.setOnAction($ -> WindowManager.toggleFullScreen());

        var runItem = new LocalizedMenuItem("railroad.menu.run.run");
        runItem.setGraphic(new FontIcon(FontAwesomeSolid.PLAY));
        runItem.setAccelerator(new KeyCodeCombination(KeyCode.F5));

        var debugItem = new LocalizedMenuItem("railroad.menu.run.debug");
        debugItem.setGraphic(new FontIcon(FontAwesomeSolid.BUG));
        debugItem.setAccelerator(new KeyCodeCombination(KeyCode.F6));

        var stopItem = new LocalizedMenuItem("railroad.menu.run.stop");
        stopItem.setGraphic(new FontIcon(FontAwesomeSolid.STOP));
        stopItem.setAccelerator(new KeyCodeCombination(KeyCode.F7));

        var settingsItem = new LocalizedMenuItem("railroad.menu.tools.settings");
        settingsItem.setGraphic(new FontIcon(FontAwesomeSolid.COG));
        settingsItem.setAccelerator(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHORTCUT_DOWN));
        settingsItem.setOnAction($ -> SettingsPane.openSettingsWindow());

        var pluginsItem = new LocalizedMenuItem("railroad.menu.tools.plugins");
        pluginsItem.setGraphic(new FontIcon(FontAwesomeSolid.PUZZLE_PIECE));
        pluginsItem.setOnAction($ -> SettingsPane.openPluginsWindow());

        var terminalItem = new LocalizedMenuItem("railroad.menu.tools.terminal");
        terminalItem.setGraphic(new FontIcon(FontAwesomeSolid.TERMINAL));
        terminalItem.setAccelerator(new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));

        var fileMenu = new LocalizedMenu("railroad.menu.file");
        fileMenu.getItems().add(newFileItem);
        fileMenu.getItems().add(openFileItem);
        fileMenu.getItems().add(saveItem);
        fileMenu.getItems().add(saveAsItem);
        fileMenu.getItems().add(separator1);
        fileMenu.getItems().add(exitItem);
        fileMenu.getStyleClass().add("rr-menu");

        var editMenu = new LocalizedMenu("railroad.menu.edit");
        editMenu.getItems().add(undoItem);
        editMenu.getItems().add(redoItem);
        editMenu.getItems().add(separator2);
        editMenu.getItems().add(cutItem);
        editMenu.getItems().add(copyItem);
        editMenu.getItems().add(pasteItem);
        editMenu.getItems().add(separator3);
        editMenu.getItems().add(findItem);
        editMenu.getItems().add(replaceItem);
        editMenu.getStyleClass().add("rr-menu");

        var viewMenu = new LocalizedMenu("railroad.menu.view");
        viewMenu.getItems().add(projectExplorerItem);
        viewMenu.getItems().add(propertiesItem);
        viewMenu.getItems().add(consoleItem);
        viewMenu.getItems().add(separator4);
        viewMenu.getItems().add(fullScreenItem);
        viewMenu.getStyleClass().add("rr-menu");

        var runMenu = new LocalizedMenu("railroad.menu.run");
        runMenu.getItems().add(runItem);
        runMenu.getItems().add(debugItem);
        runMenu.getItems().add(stopItem);
        runMenu.getStyleClass().add("rr-menu");

        var toolsMenu = new LocalizedMenu("railroad.menu.tools");
        toolsMenu.getItems().add(settingsItem);
        toolsMenu.getItems().add(pluginsItem);
        toolsMenu.getItems().add(terminalItem);
        toolsMenu.getStyleClass().add("rr-menu");

        var menuBar = new RRMenuBar(true, fileMenu, editMenu, viewMenu, runMenu, toolsMenu);
        if (OperatingSystem.isMac()) {
            menuBar.useSystemMenuBarProperty().set(true);
        }
        menuBar.getStyleClass().add("rr-menu-bar");
        return menuBar;
    }

    /**
     * Builds a tiny icon-bar with one toggle button that shows/hides
     * the given pane in the given SplitPane at the given position index.
     */
    private static Node buildPaneIconBar(
        DetachableTabPane pane,
        SplitPane split,
        Orientation orientation,
        int originalIndex,
        Map<String, String> iconsByName
    ) {
        var bar = orientation == Orientation.HORIZONTAL ? new RRHBox(4) : new RRVBox(4);
        bar.getStyleClass().add("icon-bar-" + orientation.name().toLowerCase(Locale.ROOT));

        // Map each Tab to its ToggleButton
        Map<Tab, ToggleButton> btnMap = new LinkedHashMap<>();

        // A helper to (re)create the button for a given tab
        Consumer<Tab> addButtonFor = tab -> {
            String name = tab.getText();
            String icon = iconsByName.getOrDefault(name, FontAwesomeSolid.EYE.getDescription());
            var btn = new ToggleButton("", new FontIcon(icon));
            btn.getStyleClass().add("icon-button");

            btn.setOnAction(e -> {
                boolean isVisible = split.getItems().contains(pane);
                Tab selected = pane.getSelectionModel().getSelectedItem();

                if (isVisible && selected == tab) {
                    // collapse...
                    split.getItems().remove(pane);
                    btnMap.values().forEach(b -> b.setSelected(false));
                } else {
                    if (!isVisible) {
                        split.getItems().add(Math.min(originalIndex, split.getItems().size()), pane);
                    }
                    pane.getSelectionModel().select(tab);
                    btnMap.values().forEach(b -> b.setSelected(b == btn));
                }
            });

            btnMap.put(tab, btn);
            bar.getChildren().add(btn);
        };

        // Remove button when a tab goes away
        Consumer<Tab> removeButtonFor = tab -> {
            var btn = btnMap.remove(tab);
            if (btn != null) bar.getChildren().remove(btn);
        };

        // Listen to tabs being added/removed
        pane.getTabs().addListener((ListChangeListener<Tab>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    change.getAddedSubList().forEach(addButtonFor);
                }
                if (change.wasRemoved()) {
                    change.getRemoved().forEach(removeButtonFor);
                }
            }
        });

        // initialize for existing tabs
        pane.getTabs().forEach(addButtonFor);

        // keep toggle state in sync on selection
        pane.getSelectionModel().selectedItemProperty().addListener((obs, oldT, newT) -> {
            btnMap.forEach((tab, btn) -> btn.setSelected(tab == newT));
        });

        // set the initially-selected button
        Tab init = pane.getSelectionModel().getSelectedItem();
        if (init != null) {
            btnMap.get(init).setSelected(true);
        }

        return bar;
    }


    public static Terminal createTerminal(Path path) {
        var terminalConfig = new TerminalConfig();
        terminalConfig.setBackgroundColor(Color.rgb(16, 16, 16));
        terminalConfig.setForegroundColor(Color.rgb(240, 240, 240));
        terminalConfig.setCursorColor(Color.rgb(255, 0, 0, 0.5));
        var terminalBuilder = new TerminalBuilder(terminalConfig);
        terminalBuilder.setTerminalPath(path);
        return terminalBuilder.newTerminal().getTerminal();
    }

    /**
     * Find the best tab pane for files (CodeArea) in the given parent.
     * If a welcome tab is found, it will be returned to replace it.
     * If no welcome tab is found, it will look for a tab pane with a CodeArea.
     * If no tab pane with a CodeArea is found, the first tab pane found will be returned.
     *
     * @param parent The parent to search in
     * @return The best tab pane for files
     */
    public static Optional<DetachableTabPane> findBestPaneForFiles(Parent parent) {
        // First, try to find a pane with a welcome tab to replace it
        var welcomePane = findBestPaneForFiles(parent, tab -> tab.getContent() instanceof IDEWelcomePane);
        if (welcomePane.isPresent())
            return welcomePane;

        // If no welcome tab found, fall back to the original behavior
        return findBestPaneForFiles(parent, tab -> tab.getContent() instanceof CodeArea);
    }

    /**
     * Find the best tab pane for images (ImageViewerPane) in the given parent.
     * If a tab pane with an ImageViewerPane is found, it will be returned.
     * If no tab pane with an ImageViewerPane is found, the first tab pane found will be returned.
     *
     * @param parent The parent to search in
     * @return The best tab pane for images
     */
    public static Optional<DetachableTabPane> findBestPaneForImages(Parent parent) { // TODO: Priority based search
        return findBestPaneForFiles(parent, tab -> tab.getContent() instanceof ImageViewerPane || tab.getContent() instanceof CodeArea);
    }

    /**
     * Find the best tab pane for the terminal in the given parent.
     * If a tab pane with a terminal is found, it will be returned.
     * If no tab pane with a terminal is found, the first tab pane found will be returned.
     *
     * @param parent The parent to search in
     * @return The best tab pane for the terminal
     */
    public static Optional<DetachableTabPane> findBestPaneForTerminal(Parent parent) {
        return findBestPaneForFiles(parent, tab -> tab.getContent() instanceof Terminal);
    }

    /**
     * Find the best tab pane for the files that match the given predicate in the given parent.
     * If a tab pane with a file that matches the predicate is found, it will be returned.
     *
     * @param parent    The parent to search in
     * @param predicate The predicate to match the file
     * @return The best tab pane for the files that match the predicate
     */
    private static Optional<DetachableTabPane> findBestPaneForFiles(Parent parent, Predicate<Tab> predicate) {
        var bestCandidate = new AtomicReference<DetachableTabPane>();
        Optional<DetachableTabPane> found = findBestPaneFor(parent, bestCandidate, predicate);
        return found.or(() -> Optional.ofNullable(bestCandidate.get()));
    }

    /**
     * Find the best tab pane for the given parent.
     * If a tab pane with a file that matches the predicate is found, it will be returned.
     * If no tab pane with a file that matches the predicate is found, the first tab pane found will be returned.
     *
     * @param parent        The parent to search in
     * @param bestCandidate The best candidate found so far
     * @param predicate     The predicate to match the file
     * @return The best tab pane for the files that match the predicate
     */
    private static Optional<DetachableTabPane> findBestPaneFor(Parent parent, AtomicReference<DetachableTabPane> bestCandidate, Predicate<Tab> predicate) {
        if (parent instanceof DetachableTabPane tabPane) {
            if (tabPane.getTabs().stream().anyMatch(predicate))
                return Optional.of(tabPane);
            else if (bestCandidate.get() == null || tabPane.getTabs().size() < bestCandidate.get().getTabs().size())
                bestCandidate.set(tabPane);
        }

        if (parent.getChildrenUnmodifiable().isEmpty())
            return Optional.empty();

        for (Node child : parent.getChildrenUnmodifiable()) {
            if (!(child instanceof Parent childAsParent))
                continue;

            Optional<DetachableTabPane> result = findBestPaneFor(childAsParent, bestCandidate, predicate);
            if (result.isPresent())
                return result;
        }

        return Optional.empty();
    }

    private static Node createNotImplementedPane() {
        var card = new RRCard(16);
        card.setPadding(new Insets(32, 32, 32, 32));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setAlignment(Pos.CENTER);

        var icon = new FontIcon(FontAwesomeSolid.TOOLS);
        icon.setIconSize(48);
        icon.getStyleClass().add("not-implemented-icon");

        var title = new LocalizedLabel("not_implemented.title");
        title.getStyleClass().add("not-implemented-title");
        title.setAlignment(Pos.CENTER);
        title.setTextAlignment(TextAlignment.CENTER);
        title.setWrapText(true);

        var subtitle = new LocalizedLabel("not_implemented.subtitle");
        subtitle.getStyleClass().add("not-implemented-subtitle");
        subtitle.setWrapText(true);
        subtitle.setAlignment(Pos.CENTER);
        subtitle.setTextAlignment(TextAlignment.CENTER);

        card.addContent(icon, title, subtitle);
        return card;
    }
}
