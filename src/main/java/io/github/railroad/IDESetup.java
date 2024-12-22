package io.github.railroad;

import com.kodedu.terminalfx.Terminal;
import com.kodedu.terminalfx.TerminalBuilder;
import com.kodedu.terminalfx.config.TerminalConfig;
import com.kodedu.terminalfx.helper.ThreadHelper;
import com.panemu.tiwulfx.control.dock.DetachableTab;
import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import io.github.railroad.ide.ConsolePane;
import io.github.railroad.ide.IDEWelcomePane;
import io.github.railroad.ide.ImageViewerPane;
import io.github.railroad.ide.StatusBarPane;
import io.github.railroad.ide.projectexplorer.ProjectExplorerPane;
import io.github.railroad.project.Project;
import io.github.railroad.ui.defaults.RRBorderPane;
import io.github.railroad.utility.ShutdownHooks;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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
        stage.setTitle("Railroad IDE - " + project.getAlias());

        stage.centerOnScreen();
        stage.setMaximized(true);
        stage.setMinWidth(800);
        stage.setMinHeight(600);

        var mainPane = new RRBorderPane();

        // Menu Bar
        var fileMenu = new Menu("File");

        var editMenu = new Menu("Edit");

        var viewMenu = new Menu("View");

        var runMenu = new Menu("Run");

        var helpMenu = new Menu("Help");

        var menuBar = new MenuBar(fileMenu, editMenu, viewMenu, runMenu, helpMenu);
        mainPane.setTop(menuBar);

        var horizontalSplit = new SplitPane();
        horizontalSplit.setOrientation(Orientation.HORIZONTAL);

        // File Explorer
        var leftPane = new DetachableTabPane();
        var fileExplorer = new ProjectExplorerPane(project, mainPane);
        leftPane.addTab("Project Explorer", fileExplorer);
        horizontalSplit.getItems().add(leftPane);
        horizontalSplit.setDividerPositions(0.1);

        var verticalSplit = new SplitPane();
        verticalSplit.setOrientation(Orientation.VERTICAL);

        // Center
        var centerPane = new DetachableTabPane();
        centerPane.setTabClosingPolicy(DetachableTabPane.TabClosingPolicy.ALL_TABS);
        centerPane.addTab("Welcome", new IDEWelcomePane());
        verticalSplit.getItems().add(centerPane);

        // Console
        var bottomPane = new DetachableTabPane();
        var console = new ConsolePane();
        bottomPane.addTab("Console", console);

        // Terminal
        Terminal terminal = createTerminal(Path.of(project.getPathString()));
        DetachableTab terminalTab = bottomPane.addTab("Terminal", terminal);
        bottomPane.getSelectionModel().select(terminalTab);

        verticalSplit.getItems().add(bottomPane);
        verticalSplit.setDividerPositions(0.8);

        horizontalSplit.getItems().add(verticalSplit);
        mainPane.setCenter(horizontalSplit);

        // TODO: Remove split panes if the tab panes are empty (no idea how to do this)

        // Status Bar
        var statusBar = new StatusBarPane();
        mainPane.setBottom(statusBar);

        var scene = new Scene(mainPane);
        Railroad.handleStyles(scene);
        scene.getStylesheets().add(Railroad.getResource("styles/code-area.css").toExternalForm());
        stage.getIcons().add(new Image(Railroad.getResourceAsStream("images/logo.png")));

        stage.setScene(scene);
        stage.show();

        ShutdownHooks.addHook(ThreadHelper::stopExecutorService);

        return stage;
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
     * @param parent    The parent to search in
     * @param bestCandidate The best candidate found so far
     * @param predicate The predicate to match the file
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
