package io.github.railroad;

import com.kodedu.terminalfx.Terminal;
import com.kodedu.terminalfx.TerminalBuilder;
import com.kodedu.terminalfx.config.TerminalConfig;
import com.panemu.tiwulfx.control.dock.DetachableTab;
import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import io.github.railroad.ide.*;
import io.github.railroad.ide.projectexplorer.ProjectExplorerPane;
import io.github.railroad.project.Project;
import io.github.railroad.ui.defaults.RRBorderPane;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRVBox;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class IDESetup {
    
    private static DetachableTabPane editorPane;

    public static void addEditorWindow(String path) {
        int lastDot = path.lastIndexOf(".");
        int lastDir = path.lastIndexOf("\\");
        String name = path.substring(lastDir + 1,lastDot);
        DetachableTab t = editorPane.addTab(name,new JavaCodeEditorPane(Path.of(path)));
        ImageView img = new ImageView(
                new Image(Railroad.getResourceAsStream("images/fabric.png"),16,16,true,true)
        );
        t.setGraphic(img);

    }
    public static void addPane(Pane pane) {
        DetachableTab t = editorPane.addTab(pane.getPaneName(), pane);

        t.setGraphic(new ImageView (
                new Image(pane.getLogo(),16,16,true,true)
        ));
    }

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
        rightPane.addTab("Properties", new Label("Properties Pane (not implemented yet)"));

        editorPane = new DetachableTabPane();
        editorPane.addTab("Welcome", new IDEWelcomePane());
        addPane(new Calculator());

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
        return new MenuBar(
                new Menu("File"), new Menu("Edit"),
                new Menu("View"), new Menu("Run"),
                new Menu("Help")
        );
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
     * If a tab pane with a CodeArea is found, it will be returned.
     * If no tab pane with a CodeArea is found, the first tab pane found will be returned.
     *
     * @param parent The parent to search in
     * @return The best tab pane for files
     */
    public static Optional<DetachableTabPane> findBestPaneForFiles(Parent parent) {
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
}
