package dev.railroadide.railroad.ide.ui;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.gradle.ui.GradleToolsPane;
import dev.railroadide.railroad.ide.IDEViewMode;
import dev.railroadide.railroad.ide.IDEViewModeController;
import dev.railroadide.railroad.ide.projectexplorer.ProjectExplorerPane;
import dev.railroadide.railroad.ide.ui.git.branches.GitBranchesPane;
import dev.railroadide.railroad.ide.ui.git.commit.GitCommitPane;
import dev.railroadide.railroad.ide.ui.git.commit.list.GitCommitListPane;
import dev.railroadide.railroad.ide.ui.git.overview.GitOverviewPane;
import dev.railroadide.railroad.ide.ui.git.remote.GitRemotesPane;
import dev.railroadide.railroad.ide.ui.git.stash.GitStashPane;
import dev.railroadide.railroad.ide.ui.git.sync.GitSyncPane;
import dev.railroadide.railroad.ide.ui.setup.PaneIconBarFactory;
import dev.railroadide.railroad.ide.ui.setup.TerminalFactory;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.event.EventListener;
import dev.railroadide.railroad.project.FacetDetectedEvent;
import dev.railroadide.railroad.project.facet.Facet;
import dev.railroadide.railroad.project.facet.FacetManager;
import dev.railroadide.railroad.settings.keybinds.KeybindContexts;
import dev.railroadide.railroad.settings.keybinds.KeybindHandler;
import dev.railroadide.railroad.ui.RRBorderPane;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.id.UIId;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.utility.icon.RailroadBrandsIcon;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import org.kordamp.ikonli.fontawesome6.FontAwesomeBrands;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.util.Map;
import java.util.List;
import java.util.Objects;

public final class IDEPane extends RRBorderPane {
    private final Project project;
    private final IDEPaneLifecycle lifecycle;
    private final IDEViewModeController viewModeController;

    public IDEPane(Project project) {
        this.project = Objects.requireNonNull(project, "Project cannot be null");
        this.lifecycle = new IDEPaneLifecycle(this);
        this.viewModeController = new IDEViewModeController(Services.IDE_STATE.currentViewModeProperty());
        this.lifecycle.onDispose(viewModeController::close);
        this.viewModeController.setCurrentViewMode(IDEViewMode.CODE);

        setTop(new IDETopBarPane(project, viewModeController));

        var leftPane = createLeftPane();
        var rightPane = new DetachableTabPane();
        assignWhileAttached(UIIds.IDE.IDE_RIGHT_DOCK, rightPane);
        var codeEditorPane = createCodeEditorPane();
        var gitEditorPane = createGitEditorPane();
        Map<IDEViewMode, DetachableTabPane> editorPanesByMode = Map.of(
            IDEViewMode.CODE, codeEditorPane,
            IDEViewMode.GIT, gitEditorPane
        );
        var consolePane = createBottomPane();

        var centerBottomSplit = new SplitPane(editorPanesByMode.get(viewModeController.getCurrentViewMode()), consolePane);
        centerBottomSplit.setOrientation(Orientation.VERTICAL);
        centerBottomSplit.setDividerPositions(0.75);
        viewModeController.onViewModeChanged(viewMode ->
            swapEditorPaneForViewMode(centerBottomSplit, editorPanesByMode, viewMode));

        var mainSplit = new SplitPane(leftPane, centerBottomSplit, rightPane);
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        mainSplit.setDividerPositions(0.15, 0.85);
        setCenter(mainSplit);

        configureGradlePane(rightPane, mainSplit);
        setLeft(createLeftIconBar(leftPane, mainSplit));
        setBottom(createBottomBar(consolePane, centerBottomSplit));

        KeybindHandler.registerCapture(KeybindContexts.of("railroad:ide"), this);

        assignWhileAttached(UIIds.IDE.IDE, this);
    }

    private DetachableTabPane createLeftPane() {
        var pane = new DetachableTabPane();
        var projectTab = createTab("Project", new ProjectExplorerPane(project));
        var gitOverviewTab = createTab("Git Overview", new GitOverviewPane(project));
        var gitCommitTab = createTab("Git Commit", new GitCommitPane(project));
        var gitCommitListTab = createTab("Git Commit List", new GitCommitListPane(project));
        var gitBranchesTab = createTab("Git Branches", new GitBranchesPane(project));
        var gitRemotesTab = createTab("Git Remotes", new GitRemotesPane(project));
        var gitSyncTab = createTab("Git Sync", new GitSyncPane(project));
        var gitStashTab = createTab("Git Stash", new GitStashPane(project));

        Map<IDEViewMode, List<Tab>> tabsByMode = Map.of(
            IDEViewMode.CODE, List.of(projectTab),
            IDEViewMode.GIT, List.of(
                gitOverviewTab,
                gitCommitTab,
                gitCommitListTab,
                gitBranchesTab,
                gitRemotesTab,
                gitSyncTab,
                gitStashTab
            )
        );
        viewModeController.onViewModeChanged(viewMode -> applyViewMode(pane, tabsByMode, viewMode));

        assignWhileAttached(UIIds.IDE.IDE_LEFT_DOCK, pane);
        return pane;
    }

    private DetachableTabPane createCodeEditorPane() {
        var pane = new DetachableTabPane();
        pane.addTab("Welcome", new IDEWelcomePane());

        assignWhileAttached(UIIds.IDE.IDE_EDITOR_DOCK, pane);
        return pane;
    }

    private DetachableTabPane createGitEditorPane() {
        var pane = new DetachableTabPane();
        pane.addTab("Welcome", new IDEWelcomePane());

        assignWhileAttached(UIIds.IDE.IDE_EDITOR_DOCK, pane);
        return pane;
    }

    private static Tab createTab(String title, Node content) {
        var tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private static void applyViewMode(DetachableTabPane pane, Map<IDEViewMode, List<Tab>> tabsByMode, IDEViewMode viewMode) {
        IDEViewMode resolvedMode = viewMode == null ? IDEViewMode.CODE : viewMode;
        List<Tab> tabs = tabsByMode.getOrDefault(resolvedMode, tabsByMode.get(IDEViewMode.CODE));
        pane.getTabs().setAll(tabs);
        if (!tabs.isEmpty()) {
            pane.getSelectionModel().select(tabs.getFirst());
        }
    }

    private static void swapEditorPaneForViewMode(
        SplitPane splitPane,
        Map<IDEViewMode, DetachableTabPane> editorPanesByMode,
        IDEViewMode viewMode
    ) {
        IDEViewMode resolvedMode = viewMode == null ? IDEViewMode.CODE : viewMode;
        DetachableTabPane editorPane = editorPanesByMode.getOrDefault(
            resolvedMode,
            editorPanesByMode.get(IDEViewMode.CODE)
        );

        if (splitPane.getItems().isEmpty()) {
            splitPane.getItems().add(editorPane);
            splitPane.setDividerPositions(0.75);
            return;
        }

        if (splitPane.getItems().getFirst() != editorPane) {
            double dividerPosition = splitPane.getDividerPositions().length > 0
                ? splitPane.getDividerPositions()[0]
                : 0.75;
            splitPane.getItems().set(0, editorPane);
            splitPane.setDividerPositions(dividerPosition);
        }
    }

    private DetachableTabPane createBottomPane() {
        var pane = new DetachableTabPane();
        pane.addTab("Console", new ConsolePane());
        pane.addTab("Terminal", TerminalFactory.create(project.getPath()));

        assignWhileAttached(UIIds.IDE.IDE_BOTTOM_DOCK, pane);
        return pane;
    }

    private void configureGradlePane(DetachableTabPane rightPane, SplitPane mainSplit) {
        if (project.hasFacet(FacetManager.GRADLE)) {
            openGradleTab(project.getFacet(FacetManager.GRADLE).orElseThrow(), rightPane, mainSplit);
        }

        EventListener<FacetDetectedEvent> facetDetectedListener = event -> {
            if (event.project() == project) {
                openGradleTab(event.facet(), rightPane, mainSplit);
            }
        };
        Railroad.EVENT_BUS.subscribe(FacetDetectedEvent.class, facetDetectedListener);
        lifecycle.onDispose(() -> Railroad.EVENT_BUS.unsubscribe(FacetDetectedEvent.class, facetDetectedListener));
    }

    private void openGradleTab(Facet<?> facet, DetachableTabPane rightPane, SplitPane mainSplit) {
        Platform.runLater(() -> {
            if (facet.getType() != FacetManager.GRADLE || rightPane.getTabs().stream()
                .anyMatch(tab -> tab.getContent() instanceof GradleToolsPane)) {
                return;
            }

            rightPane.addTab("Gradle", new GradleToolsPane(project));
            setRight(PaneIconBarFactory.create(
                rightPane,
                mainSplit,
                Orientation.VERTICAL,
                2,
                Map.of("Gradle", RailroadBrandsIcon.GRADLE.getDescription())
            ));
        });
    }

    private static Node createLeftIconBar(DetachableTabPane leftPane, SplitPane mainSplit) {
        return PaneIconBarFactory.create(
            leftPane,
            mainSplit,
            Orientation.VERTICAL,
            0,
            Map.of(
                "Project", FontAwesomeSolid.FOLDER.getDescription(),
                "Git Commit", FontAwesomeBrands.USB.getDescription(),
                "Git Overview", FontAwesomeSolid.HOME.getDescription(),
                "Git Commit List", FontAwesomeSolid.LIST.getDescription(),
                "Git Branches", FontAwesomeSolid.CODE_BRANCH.getDescription(),
                "Git Remotes", FontAwesomeSolid.GLOBE.getDescription(),
                "Git Sync", FontAwesomeSolid.SYNC.getDescription(),
                "Git Stash", FontAwesomeSolid.BOX.getDescription()
            )
        );
    }

    private static RRVBox createBottomBar(DetachableTabPane consolePane, SplitPane centerBottomSplit) {
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
        bottomBar.getChildren().addAll(bottomIcons, new IDEStatusBarPane());
        return bottomBar;
    }

    private <T extends Node> void assignWhileAttached(UIId<T> id, T node) {
        var registration = Services.UI_MANAGER.assignWhileAttached(id, node);
        lifecycle.onDispose(registration::close);
    }
}
