package io.github.railroad;

import com.kodedu.terminalfx.TerminalBuilder;
import com.kodedu.terminalfx.config.TerminalConfig;
import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import io.github.railroad.ide.*;
import io.github.railroad.project.Project;
import io.github.railroad.ui.defaults.RRBorderPane;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.nio.file.Path;

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
        var fileExplorer = new FileExplorerPane(project);
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

        stage.setScene(scene);
        stage.show();

        return stage;
    }
}
