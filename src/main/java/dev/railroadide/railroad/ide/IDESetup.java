package dev.railroadide.railroad.ide;

import com.kodedu.terminalfx.Terminal;
import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.core.settings.keybinds.KeybindContexts;
import dev.railroadide.core.ui.RRBorderPane;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.projectexplorer.ProjectExplorerPane;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.ui.RunConfigurationEditorPane;
import dev.railroadide.railroad.ide.ui.ConsolePane;
import dev.railroadide.railroad.ide.ui.IDEWelcomePane;
import dev.railroadide.railroad.ide.ui.ImageViewerPane;
import dev.railroadide.railroad.ide.ui.StatusBarPane;
import dev.railroadide.railroad.ide.ui.setup.*;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.settings.keybinds.KeybindHandler;
import dev.railroadide.railroad.window.WindowBuilder;
import dev.railroadide.railroadpluginapi.events.ProjectEvent;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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
        var topBar = new RRHBox(IDEMenuBarFactory.create(), new Region(), RunControlsPane.create(project));
        HBox.setHgrow(topBar.getChildren().get(1), Priority.ALWAYS);
        root.setTop(topBar);

        var leftPane = new DetachableTabPane();
        leftPane.addTab("Project", new ProjectExplorerPane(project, root));

        var rightPane = new DetachableTabPane();
        rightPane.addTab("Properties", NotImplementedPaneFactory.create());

        var editorPane = new DetachableTabPane();
        editorPane.addTab("Welcome", new IDEWelcomePane());

        var consolePane = new DetachableTabPane();
        consolePane.addTab("Console", new ConsolePane());
        consolePane.addTab("Terminal", TerminalFactory.create(project.getPath()));

        var centerBottomSplit = new SplitPane(editorPane, consolePane);
        centerBottomSplit.setOrientation(Orientation.VERTICAL);
        centerBottomSplit.setDividerPositions(0.75);

        var mainSplit = new SplitPane(leftPane, centerBottomSplit, rightPane);
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        mainSplit.setDividerPositions(0.15, 0.85);
        root.setCenter(mainSplit);

        root.setLeft(PaneIconBarFactory.create(
            leftPane,
            mainSplit,
            Orientation.VERTICAL,
            0,
            Map.of("Project", FontAwesomeSolid.FOLDER.getDescription())
        ));

        root.setRight(PaneIconBarFactory.create(
            rightPane,
            mainSplit,
            Orientation.VERTICAL,
            2,
            Map.of("Properties", FontAwesomeSolid.INFO_CIRCLE.getDescription())
        ));

        var bottomBar = new RRVBox();
        var bottomIcons = PaneIconBarFactory.create(
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

}
