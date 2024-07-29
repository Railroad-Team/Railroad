package io.github.railroad;

import com.kodedu.terminalfx.TerminalBuilder;
import com.kodedu.terminalfx.config.TerminalConfig;
import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import io.github.railroad.ide.ConsolePane;
import io.github.railroad.ide.FileExplorerPane;
import io.github.railroad.ide.IDEWelcomePane;
import io.github.railroad.ide.StatusBarPane;
import io.github.railroad.project.Project;
import io.github.railroad.ui.defaults.RRBorderPane;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class IDESetup {
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
        var fileExplorer = new FileExplorerPane(project, mainPane);
        leftPane.addTab("File Explorer", fileExplorer);
        horizontalSplit.getItems().add(leftPane);

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
        var terminalConfig = new TerminalConfig();
        terminalConfig.setBackgroundColor(Color.rgb(16, 16, 16));
        terminalConfig.setForegroundColor(Color.rgb(240, 240, 240));
        terminalConfig.setCursorColor(Color.rgb(255, 0, 0, 0.5));
        var terminalBuilder = new TerminalBuilder(terminalConfig);
        terminalBuilder.setTerminalPath(Path.of(project.getPathString()));
        var terminal = terminalBuilder.newTerminal().getTerminal();
        bottomPane.addTab("Terminal", terminal);

        verticalSplit.getItems().add(bottomPane);
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

        return stage;
    }

    // search for a tab pane that has a code area in it and if it cant find one, return the first tab pane it finds
    public static Optional<DetachableTabPane> findBestPaneForFiles(Parent parent) {
        var bestCandidate = new AtomicReference<DetachableTabPane>();
        Optional<DetachableTabPane> found = findBestPaneForFiles(parent, bestCandidate);
        return found.or(() -> Optional.ofNullable(bestCandidate.get()));
    }

    private static Optional<DetachableTabPane> findBestPaneForFiles(Parent parent, AtomicReference<DetachableTabPane> bestCandidate) {
        if (parent instanceof DetachableTabPane tabPane) {
            if (tabPane.getTabs().stream().anyMatch(tab -> tab.getContent() instanceof CodeArea))
                return Optional.of(tabPane);
            else if (bestCandidate.get() == null || tabPane.getTabs().size() < bestCandidate.get().getTabs().size())
                bestCandidate.set(tabPane);
        }

        if (parent.getChildrenUnmodifiable().isEmpty())
            return Optional.empty();

        for (Node child : parent.getChildrenUnmodifiable()) {
            if (!(child instanceof Parent childAsParent))
                continue;

            Optional<DetachableTabPane> result = findBestPaneForFiles(childAsParent, bestCandidate);
            if (result.isPresent())
                return result;
        }

        return Optional.empty();
    }
}
