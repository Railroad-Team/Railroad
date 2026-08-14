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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class IDEPane extends RRBorderPane {
    private final Project project;
    private final IDEPaneLifecycle lifecycle;
    private final IDEViewModeController viewModeController;
    private final IDEContentRouter contentRouter;

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
        Map<IDEViewMode, DetachableTabPane> editorPanesByMode = new EnumMap<>(IDEViewMode.class);
        var codeEditorPane = getOrCreateEditorPane(editorPanesByMode, IDEViewMode.CODE);
        this.contentRouter = new IDEContentRouter(viewModeController);
        var consolePane = createBottomPane();

        var centerBottomSplit = new SplitPane(codeEditorPane, consolePane);
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
        Map<IDEViewMode, List<Tab>> tabsByMode = new EnumMap<>(IDEViewMode.class);
        tabsByMode.put(IDEViewMode.CODE, List.of(projectTab));
        viewModeController.onViewModeChanged(viewMode -> {
            if (viewMode == IDEViewMode.GIT) {
                tabsByMode.computeIfAbsent(IDEViewMode.GIT, _ -> createGitToolTabs());
            }
            applyViewMode(pane, tabsByMode, viewMode);
        });

        assignWhileAttached(UIIds.IDE.IDE_LEFT_DOCK, pane);
        return pane;
    }

    private List<Tab> createGitToolTabs() {
        return List.of(
            createLazyTab("Git Overview", () -> new GitOverviewPane(project)),
            createLazyTab("Git Commit", () -> new GitCommitPane(project)),
            createLazyTab("Git Commit List", () -> new GitCommitListPane(project)),
            createLazyTab("Git Branches", () -> new GitBranchesPane(project)),
            createLazyTab("Git Remotes", () -> new GitRemotesPane(project)),
            createLazyTab("Git Sync", () -> new GitSyncPane(project)),
            createLazyTab("Git Stash", () -> new GitStashPane(project))
        );
    }

    private DetachableTabPane createCodeEditorPane() {
        var pane = new DetachableTabPane();
        pane.addTab("Welcome", new IDEWelcomePane());

        assignWhileIDEAttached(UIIds.IDE.IDE_CODE_EDITOR_DOCK, pane);
        return pane;
    }

    private DetachableTabPane createGitEditorPane() {
        var pane = new DetachableTabPane();
        pane.addTab("Welcome", new IDEWelcomePane());

        assignWhileIDEAttached(UIIds.IDE.IDE_GIT_EDITOR_DOCK, pane);
        return pane;
    }

    IDEContentRouter getContentRouter() {
        return contentRouter;
    }

    private DetachableTabPane getOrCreateEditorPane(
        Map<IDEViewMode, DetachableTabPane> editorPanesByMode,
        IDEViewMode viewMode
    ) {
        IDEViewMode resolvedMode = viewMode == null ? IDEViewMode.CODE : viewMode;
        return editorPanesByMode.computeIfAbsent(resolvedMode, mode -> switch (mode) {
            case CODE -> createCodeEditorPane();
            case GIT -> createGitEditorPane();
        });
    }

    private static Tab createTab(String title, Node content) {
        var tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private static Tab createLazyTab(String title, Supplier<? extends Node> contentFactory) {
        Objects.requireNonNull(contentFactory, "Content factory cannot be null");

        var tab = new Tab(title);
        tab.setClosable(false);
        tab.setOnSelectionChanged(_ -> {
            if (tab.isSelected() && tab.getContent() == null) {
                tab.setContent(contentFactory.get());
                tab.setOnSelectionChanged(null);
            }
        });
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

    private void swapEditorPaneForViewMode(
        SplitPane splitPane,
        Map<IDEViewMode, DetachableTabPane> editorPanesByMode,
        IDEViewMode viewMode
    ) {
        DetachableTabPane editorPane = getOrCreateEditorPane(editorPanesByMode, viewMode);

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

    private <T extends Node> void assignWhileIDEAttached(UIId<T> id, T node) {
        var registration = Services.UI_MANAGER.assignWhileAttached(id, this, node);
        lifecycle.onDispose(registration::close);
    }
}
