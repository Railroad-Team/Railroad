package io.github.railroad;

import com.kodedu.terminalfx.Terminal;
import com.kodedu.terminalfx.TerminalBuilder;
import com.kodedu.terminalfx.config.TerminalConfig;
import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import io.github.railroad.ide.ConsolePane;
import io.github.railroad.ide.IDEWelcomePane;
import io.github.railroad.ide.ImageViewerPane;
import io.github.railroad.ide.StatusBarPane;
import io.github.railroad.ide.projectexplorer.ProjectExplorerPane;
import io.github.railroad.project.Project;
import io.github.railroad.ui.defaults.RRBorderPane;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.ui.nodes.RRCard;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.application.Platform;
import org.fxmisc.richtext.CodeArea;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import io.github.railroad.localization.L18n;
import io.github.railroad.localization.ui.LocalizedLabel;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class IDESetup {
    /**
     * Create a new IDE window for the given project.
     *
     * @param project The project to create the IDE window for
     * @return The created IDE window
     */
    public static Stage createIDEWindow(Project project) {
        var stage = new Stage();
        stage.setTitle("Railroad IDE – " + project.getAlias());
        stage.setMaximized(true);

        var root = new RRBorderPane();
        root.setTop(createMenuBar());

        var leftPane = new DetachableTabPane();
        leftPane.addTab("Project", new ProjectExplorerPane(project, root));

        var rightPane = new DetachableTabPane();
        rightPane.addTab("Properties", createNotImplementedPane());

        var editorPane = new DetachableTabPane();
        editorPane.addTab("Welcome", new IDEWelcomePane());

        var consolePane = new DetachableTabPane();
        consolePane.addTab("Console", new ConsolePane());
        consolePane.addTab("Terminal", createTerminal(Path.of(project.getPathString())));

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
                        "Console",  FontAwesomeSolid.PLAY_CIRCLE.getDescription(),
                        "Terminal", FontAwesomeSolid.TERMINAL.getDescription()
                )
        );
        bottomBar.getChildren().addAll(
                bottomIcons,
                new StatusBarPane()
        );
        root.setBottom(bottomBar);

        var scene = new Scene(root, Screen.getPrimary().getVisualBounds().getWidth() * 0.8,
                Screen.getPrimary().getVisualBounds().getHeight() * 0.8);
        Railroad.handleStyles(scene);
        stage.getIcons().add(new Image(Railroad.getResourceAsStream("images/logo.png")));
        stage.setScene(scene);
        stage.show();
        return stage;
    }

    private static MenuBar createMenuBar() {
        var menuBar = new MenuBar();
        menuBar.getStyleClass().add("rr-menu-bar");

        var fileMenu = new Menu(L18n.localize("railroad.menu.file"));
        fileMenu.getStyleClass().add("rr-menu");

        fileMenu.getItems().clear();
        
        var newFileItem = new MenuItem(L18n.localize("railroad.menu.file.new_file"));
        newFileItem.setGraphic(new FontIcon(FontAwesomeSolid.FILE));
        newFileItem.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        
        var openFileItem = new MenuItem(L18n.localize("railroad.menu.file.open_file"));
        openFileItem.setGraphic(new FontIcon(FontAwesomeSolid.FOLDER_OPEN));
        openFileItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        
        var saveItem = new MenuItem(L18n.localize("railroad.menu.file.save"));
        saveItem.setGraphic(new FontIcon(FontAwesomeSolid.SAVE));
        saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        
        var saveAsItem = new MenuItem(L18n.localize("railroad.menu.file.save_as"));
        saveAsItem.setGraphic(new FontIcon(FontAwesomeSolid.SAVE));
        saveAsItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        
        var separator1 = new SeparatorMenuItem();
        
        var exitItem = new MenuItem(L18n.localize("railroad.menu.file.exit"));
        exitItem.setGraphic(new FontIcon(FontAwesomeSolid.SIGN_OUT_ALT));
        exitItem.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN));
        exitItem.setOnAction(e -> Platform.exit());

        fileMenu.getItems().addAll(
                newFileItem, openFileItem, separator1,
                saveItem, saveAsItem, separator1,
                exitItem
        );

        var editMenu = new Menu(L18n.localize("railroad.menu.edit"));
        editMenu.getStyleClass().add("rr-menu");

        editMenu.getItems().clear();
        
        var undoItem = new MenuItem(L18n.localize("railroad.menu.edit.undo"));
        undoItem.setGraphic(new FontIcon(FontAwesomeSolid.UNDO));
        undoItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        
        var redoItem = new MenuItem(L18n.localize("railroad.menu.edit.redo"));
        redoItem.setGraphic(new FontIcon(FontAwesomeSolid.REDO));
        redoItem.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
        
        var separator2 = new SeparatorMenuItem();
        
        var cutItem = new MenuItem(L18n.localize("railroad.menu.edit.cut"));
        cutItem.setGraphic(new FontIcon(FontAwesomeSolid.CUT));
        cutItem.setAccelerator(new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN));
        
        var copyItem = new MenuItem(L18n.localize("railroad.menu.edit.copy"));
        copyItem.setGraphic(new FontIcon(FontAwesomeSolid.COPY));
        copyItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN));
        
        var pasteItem = new MenuItem(L18n.localize("railroad.menu.edit.paste"));
        pasteItem.setGraphic(new FontIcon(FontAwesomeSolid.PASTE));
        pasteItem.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN));
        
        var separator3 = new SeparatorMenuItem();
        
        var findItem = new MenuItem(L18n.localize("railroad.menu.edit.find"));
        findItem.setGraphic(new FontIcon(FontAwesomeSolid.SEARCH));
        findItem.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN));
        
        var replaceItem = new MenuItem(L18n.localize("railroad.menu.edit.replace"));
        replaceItem.setGraphic(new FontIcon(FontAwesomeSolid.SEARCH_PLUS));
        replaceItem.setAccelerator(new KeyCodeCombination(KeyCode.H, KeyCombination.CONTROL_DOWN));

        editMenu.getItems().addAll(
                undoItem, redoItem, separator2,
                cutItem, copyItem, pasteItem, separator3,
                findItem, replaceItem
        );

        var viewMenu = new Menu(L18n.localize("railroad.menu.view"));
        viewMenu.getStyleClass().add("rr-menu");

        viewMenu.getItems().clear();
        
        var projectExplorerItem = new CheckMenuItem(L18n.localize("railroad.menu.view.project_explorer"));
        projectExplorerItem.setGraphic(new FontIcon(FontAwesomeSolid.FOLDER));
        projectExplorerItem.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.CONTROL_DOWN));
        
        var propertiesItem = new CheckMenuItem(L18n.localize("railroad.menu.view.properties"));
        propertiesItem.setGraphic(new FontIcon(FontAwesomeSolid.INFO_CIRCLE));
        propertiesItem.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.CONTROL_DOWN));
        
        var consoleItem = new CheckMenuItem(L18n.localize("railroad.menu.view.console"));
        consoleItem.setGraphic(new FontIcon(FontAwesomeSolid.TERMINAL));
        consoleItem.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT3, KeyCombination.CONTROL_DOWN));
        
        var separator4 = new SeparatorMenuItem();
        
        var fullScreenItem = new MenuItem(L18n.localize("railroad.menu.view.full_screen"));
        fullScreenItem.setGraphic(new FontIcon(FontAwesomeSolid.EXPAND));
        fullScreenItem.setAccelerator(new KeyCodeCombination(KeyCode.F11));

        viewMenu.getItems().addAll(
                projectExplorerItem, propertiesItem, consoleItem, separator4,
                fullScreenItem
        );

        var runMenu = new Menu(L18n.localize("railroad.menu.run"));
        runMenu.getStyleClass().add("rr-menu");

        runMenu.getItems().clear();
        
        var runItem = new MenuItem(L18n.localize("railroad.menu.run.run"));
        runItem.setGraphic(new FontIcon(FontAwesomeSolid.PLAY));
        runItem.setAccelerator(new KeyCodeCombination(KeyCode.F5));
        
        var debugItem = new MenuItem(L18n.localize("railroad.menu.run.debug"));
        debugItem.setGraphic(new FontIcon(FontAwesomeSolid.BUG));
        debugItem.setAccelerator(new KeyCodeCombination(KeyCode.F6));
        
        var stopItem = new MenuItem(L18n.localize("railroad.menu.run.stop"));
        stopItem.setGraphic(new FontIcon(FontAwesomeSolid.STOP));
        stopItem.setAccelerator(new KeyCodeCombination(KeyCode.F7));

        runMenu.getItems().addAll(
                runItem, debugItem, stopItem
        );

        var toolsMenu = new Menu(L18n.localize("railroad.menu.tools"));
        toolsMenu.getStyleClass().add("rr-menu");

        toolsMenu.getItems().clear();
        
        var settingsItem = new MenuItem(L18n.localize("railroad.menu.tools.settings"));
        settingsItem.setGraphic(new FontIcon(FontAwesomeSolid.COG));
        settingsItem.setAccelerator(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.CONTROL_DOWN));
        
        var pluginsItem = new MenuItem(L18n.localize("railroad.menu.tools.plugins"));
        pluginsItem.setGraphic(new FontIcon(FontAwesomeSolid.PUZZLE_PIECE));
        
        var terminalItem = new MenuItem(L18n.localize("railroad.menu.tools.terminal"));
        terminalItem.setGraphic(new FontIcon(FontAwesomeSolid.TERMINAL));
        terminalItem.setAccelerator(new KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));

        toolsMenu.getItems().addAll(
                settingsItem, pluginsItem, terminalItem
        );

        var helpMenu = new Menu(L18n.localize("railroad.menu.help"));
        helpMenu.getStyleClass().add("rr-menu");

        helpMenu.getItems().clear();
        
        var documentationItem = new MenuItem(L18n.localize("railroad.menu.help.documentation"));
        documentationItem.setGraphic(new FontIcon(FontAwesomeSolid.BOOK));
        documentationItem.setAccelerator(new KeyCodeCombination(KeyCode.F1));
        documentationItem.setOnAction(e -> Railroad.openUrl("https://railroad.turtywurty.dev/wiki"));
        
        var tutorialsItem = new MenuItem(L18n.localize("railroad.menu.help.tutorials"));
        tutorialsItem.setGraphic(new FontIcon(FontAwesomeSolid.GRADUATION_CAP));
        tutorialsItem.setOnAction(e -> Railroad.openUrl("https://tutorials.turtywurty.dev/"));
        
        var separator5 = new SeparatorMenuItem();
        
        var aboutItem = new MenuItem(L18n.localize("railroad.menu.help.about"));
        aboutItem.setGraphic(new FontIcon(FontAwesomeSolid.INFO));

        helpMenu.getItems().addAll(
                documentationItem, tutorialsItem, separator5, aboutItem
        );

        menuBar.getMenus().clear();
        menuBar.getMenus().addAll(fileMenu, editMenu, viewMenu, runMenu, toolsMenu, helpMenu);
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
