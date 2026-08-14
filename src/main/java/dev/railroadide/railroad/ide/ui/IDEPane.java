package dev.railroadide.railroad.ide.ui;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.gradle.ui.GradleToolsPane;
import dev.railroadide.railroad.ide.IDELayoutState;
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
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
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
    private final Map<IDEViewMode, List<Tab>> toolTabsByMode = new EnumMap<>(IDEViewMode.class);
    private final Map<IDEViewMode, DetachableTabPane> editorPanesByMode = new EnumMap<>(IDEViewMode.class);
    private final Map<IDEViewMode, IDELayoutState.ModeLayout> layoutsByMode = new EnumMap<>(IDEViewMode.class);

    private final DetachableTabPane leftPane;
    private final DetachableTabPane rightPane;
    private final DetachableTabPane bottomPane;
    private final SplitPane centerBottomSplit;
    private final SplitPane mainSplit;

    private IDEViewMode activeViewMode;
    private boolean layoutInitialized;
    private boolean layoutTransitioning;

    public IDEPane(Project project) {
        this.project = Objects.requireNonNull(project, "Project cannot be null");
        this.lifecycle = new IDEPaneLifecycle(this);
        this.viewModeController = new IDEViewModeController(Services.IDE_STATE.currentViewModeProperty());
        this.lifecycle.onDispose(viewModeController::close);
        this.viewModeController.setCurrentViewMode(IDEViewMode.CODE);

        setTop(new IDETopBarPane(project, viewModeController));

        this.leftPane = createLeftPane();
        this.rightPane = new DetachableTabPane();
        assignWhileAttached(UIIds.IDE.IDE_RIGHT_DOCK, rightPane);
        var codeEditorPane = getOrCreateEditorPane(editorPanesByMode, IDEViewMode.CODE);
        this.contentRouter = new IDEContentRouter(viewModeController);
        this.bottomPane = createBottomPane();

        this.centerBottomSplit = new SplitPane(codeEditorPane, bottomPane);
        centerBottomSplit.setOrientation(Orientation.VERTICAL);
        centerBottomSplit.setDividerPositions(0.75);

        this.mainSplit = new SplitPane(leftPane, centerBottomSplit, rightPane);
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        mainSplit.setDividerPositions(0.15, 0.85);
        setCenter(mainSplit);

        configureGradlePane(rightPane, mainSplit);
        setLeft(createLeftIconBar(leftPane, mainSplit));
        setBottom(createBottomBar(bottomPane, centerBottomSplit));
        installLayoutTracking();

        viewModeController.onViewModeChanged(this::activateViewMode);

        KeybindHandler.registerCapture(KeybindContexts.of("railroad:ide"), this);

        assignWhileAttached(UIIds.IDE.IDE, this);
    }

    private DetachableTabPane createLeftPane() {
        var pane = new DetachableTabPane();
        var projectTab = createTab("tool:project", "Project", new ProjectExplorerPane(project));
        toolTabsByMode.put(IDEViewMode.CODE, List.of(projectTab));

        assignWhileAttached(UIIds.IDE.IDE_LEFT_DOCK, pane);
        return pane;
    }

    private List<Tab> createGitToolTabs() {
        return List.of(
            createLazyTab("tool:git-overview", "Git Overview", () -> new GitOverviewPane(project)),
            createLazyTab("tool:git-commit", "Git Commit", () -> new GitCommitPane(project)),
            createLazyTab("tool:git-commit-list", "Git Commit List", () -> new GitCommitListPane(project)),
            createLazyTab("tool:git-branches", "Git Branches", () -> new GitBranchesPane(project)),
            createLazyTab("tool:git-remotes", "Git Remotes", () -> new GitRemotesPane(project)),
            createLazyTab("tool:git-sync", "Git Sync", () -> new GitSyncPane(project)),
            createLazyTab("tool:git-stash", "Git Stash", () -> new GitStashPane(project))
        );
    }

    private DetachableTabPane createCodeEditorPane() {
        var pane = new DetachableTabPane();
        pane.getTabs().add(createTab("editor:welcome", "Welcome", new IDEWelcomePane()));
        trackSelectedTab(pane);

        assignWhileIDEAttached(UIIds.IDE.IDE_CODE_EDITOR_DOCK, pane);
        return pane;
    }

    private DetachableTabPane createGitEditorPane() {
        var pane = new DetachableTabPane();
        pane.getTabs().add(createTab("editor:git-welcome", "Welcome", new IDEWelcomePane()));
        trackSelectedTab(pane);

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

    private static Tab createTab(String id, String title, Node content) {
        var tab = new Tab(title, content);
        tab.setId(id);
        tab.setClosable(false);
        return tab;
    }

    private static Tab createLazyTab(String id, String title, Supplier<? extends Node> contentFactory) {
        Objects.requireNonNull(contentFactory, "Content factory cannot be null");

        var tab = new Tab(title);
        tab.setId(id);
        tab.setClosable(false);
        tab.setOnSelectionChanged(_ -> {
            if (tab.isSelected() && tab.getContent() == null) {
                tab.setContent(contentFactory.get());
                tab.setOnSelectionChanged(null);
            }
        });
        return tab;
    }

    private void activateViewMode(IDEViewMode viewMode) {
        IDEViewMode resolvedMode = viewMode == null ? IDEViewMode.CODE : viewMode;
        if (layoutInitialized && activeViewMode != null && activeViewMode != resolvedMode) {
            layoutsByMode.put(activeViewMode, captureModeLayout(activeViewMode));
        }

        layoutTransitioning = true;
        try {
            if (resolvedMode == IDEViewMode.GIT) {
                toolTabsByMode.computeIfAbsent(IDEViewMode.GIT, _ -> createGitToolTabs());
            }

            DetachableTabPane editorPane = getOrCreateEditorPane(editorPanesByMode, resolvedMode);
            leftPane.getTabs().setAll(toolTabsByMode.getOrDefault(resolvedMode, toolTabsByMode.get(IDEViewMode.CODE)));
            replaceEditorPane(editorPane);

            activeViewMode = resolvedMode;
            restoreModeLayout(layoutsByMode.getOrDefault(resolvedMode, IDELayoutState.ModeLayout.defaults()), editorPane);
            layoutInitialized = true;
            layoutsByMode.put(resolvedMode, captureModeLayout(resolvedMode));
        } finally {
            layoutTransitioning = false;
        }
    }

    private void replaceEditorPane(DetachableTabPane editorPane) {
        if (centerBottomSplit.getItems().isEmpty()) {
            centerBottomSplit.getItems().add(editorPane);
        } else if (centerBottomSplit.getItems().getFirst() != editorPane) {
            centerBottomSplit.getItems().set(0, editorPane);
        }
    }

    private IDELayoutState.ModeLayout captureModeLayout(IDEViewMode viewMode) {
        IDELayoutState.ModeLayout previous = layoutsByMode.getOrDefault(viewMode, IDELayoutState.ModeLayout.defaults());
        boolean leftVisible = mainSplit.getItems().contains(leftPane);
        boolean rightVisible = mainSplit.getItems().contains(rightPane);
        boolean bottomVisible = centerBottomSplit.getItems().contains(bottomPane);

        double leftDivider = previous.leftDividerPosition();
        double rightDivider = previous.rightDividerPosition();
        double bottomDivider = previous.bottomDividerPosition();
        double[] mainDividers = mainSplit.getDividerPositions();
        if (leftVisible && rightVisible && mainDividers.length >= 2) {
            leftDivider = mainDividers[0];
            rightDivider = mainDividers[1];
        } else if (leftVisible && mainDividers.length >= 1) {
            leftDivider = mainDividers[0];
        } else if (rightVisible && mainDividers.length >= 1) {
            rightDivider = mainDividers[0];
        }

        double[] bottomDividers = centerBottomSplit.getDividerPositions();
        if (bottomVisible && bottomDividers.length >= 1) {
            bottomDivider = bottomDividers[0];
        }

        return new IDELayoutState.ModeLayout(
            selectedTabIdentity(leftPane),
            selectedTabIdentity(editorPanesByMode.get(viewMode)),
            selectedTabIdentity(rightPane),
            selectedTabIdentity(bottomPane),
            leftDivider,
            rightDivider,
            bottomDivider,
            leftVisible,
            rightVisible,
            bottomVisible
        );
    }

    private void restoreModeLayout(IDELayoutState.ModeLayout layout, DetachableTabPane editorPane) {
        setDockVisible(mainSplit, leftPane, layout.leftDockVisible(), 0);
        setDockVisible(mainSplit, rightPane, layout.rightDockVisible(), 2);
        setDockVisible(centerBottomSplit, bottomPane, layout.bottomDockVisible(), 1);

        restoreMainDividerPositions(layout);
        if (layout.bottomDockVisible()) {
            centerBottomSplit.setDividerPositions(clampDivider(layout.bottomDividerPosition(), 0.75));
        }

        selectTab(leftPane, layout.selectedLeftTab());
        selectTab(editorPane, layout.selectedEditorTab());
        selectTab(rightPane, layout.selectedRightTab());
        selectTab(bottomPane, layout.selectedBottomTab());
    }

    private void restoreMainDividerPositions(IDELayoutState.ModeLayout layout) {
        boolean leftVisible = mainSplit.getItems().contains(leftPane);
        boolean rightVisible = mainSplit.getItems().contains(rightPane);
        double leftDivider = clampDivider(layout.leftDividerPosition(), 0.15);
        double rightDivider = clampDivider(layout.rightDividerPosition(), 0.85);

        if (leftVisible && rightVisible) {
            if (leftDivider >= rightDivider) {
                leftDivider = 0.15;
                rightDivider = 0.85;
            }
            mainSplit.setDividerPositions(leftDivider, rightDivider);
        } else if (leftVisible) {
            mainSplit.setDividerPositions(leftDivider);
        } else if (rightVisible) {
            mainSplit.setDividerPositions(rightDivider);
        }
    }

    private static void setDockVisible(SplitPane splitPane, Node dock, boolean visible, int preferredIndex) {
        boolean currentlyVisible = splitPane.getItems().contains(dock);
        if (visible && !currentlyVisible) {
            splitPane.getItems().add(Math.min(preferredIndex, splitPane.getItems().size()), dock);
        } else if (!visible && currentlyVisible) {
            splitPane.getItems().remove(dock);
        }
    }

    private static String selectedTabIdentity(DetachableTabPane pane) {
        if (pane == null)
            return null;

        Tab selectedTab = pane.getSelectionModel().getSelectedItem();
        return selectedTab == null ? null : tabIdentity(selectedTab);
    }

    private static String tabIdentity(Tab tab) {
        String id = tab.getId();
        if (id != null && !id.isBlank())
            return "id:" + id;

        Node content = tab.getContent();
        if (content != null)
            return "content:" + content.getClass().getName();

        return "title:" + Objects.toString(tab.getText(), "");
    }

    private static void selectTab(DetachableTabPane pane, String identity) {
        if (pane.getTabs().isEmpty())
            return;

        if (identity != null) {
            pane.getTabs().stream()
                .filter(tab -> identity.equals(tabIdentity(tab)))
                .findFirst()
                .ifPresentOrElse(
                    pane.getSelectionModel()::select,
                    () -> pane.getSelectionModel().selectFirst()
                );
        } else {
            pane.getSelectionModel().selectFirst();
        }
    }

    private static double clampDivider(double value, double fallback) {
        return value > 0.0 && value < 1.0 ? value : fallback;
    }

    private void installLayoutTracking() {
        trackSelectedTab(leftPane);
        trackSelectedTab(rightPane);
        trackSelectedTab(bottomPane);
        trackSplitPane(mainSplit);
        trackSplitPane(centerBottomSplit);
    }

    private void trackSelectedTab(DetachableTabPane pane) {
        ChangeListener<Tab> listener = (_, _, _) -> snapshotActiveLayout();
        pane.getSelectionModel().selectedItemProperty().addListener(listener);
        lifecycle.onDispose(() -> pane.getSelectionModel().selectedItemProperty().removeListener(listener));
    }

    private void trackSplitPane(SplitPane splitPane) {
        ChangeListener<Number> positionListener = (_, _, _) -> snapshotActiveLayout();
        ListChangeListener<SplitPane.Divider> dividerListener = change -> {
            while (change.next()) {
                change.getRemoved().forEach(divider -> divider.positionProperty().removeListener(positionListener));
                change.getAddedSubList().forEach(divider -> divider.positionProperty().addListener(positionListener));
            }
            snapshotActiveLayout();
        };
        ListChangeListener<Node> itemListener = _ -> snapshotActiveLayout();

        splitPane.getDividers().forEach(divider -> divider.positionProperty().addListener(positionListener));
        splitPane.getDividers().addListener(dividerListener);
        splitPane.getItems().addListener(itemListener);
        lifecycle.onDispose(() -> {
            splitPane.getItems().removeListener(itemListener);
            splitPane.getDividers().removeListener(dividerListener);
            splitPane.getDividers().forEach(divider -> divider.positionProperty().removeListener(positionListener));
        });
    }

    private void snapshotActiveLayout() {
        if (!layoutTransitioning && layoutInitialized && activeViewMode != null) {
            layoutsByMode.put(activeViewMode, captureModeLayout(activeViewMode));
        }
    }

    public IDELayoutState captureLayoutState() {
        if (Platform.isFxApplicationThread() && layoutInitialized && activeViewMode != null) {
            layoutsByMode.put(activeViewMode, captureModeLayout(activeViewMode));
        }
        IDEViewMode currentMode = activeViewMode == null ? IDEViewMode.CODE : activeViewMode;
        return new IDELayoutState(currentMode, layoutsByMode);
    }

    public void restoreLayoutState(IDELayoutState layoutState) {
        if (layoutState == null)
            return;

        Runnable restoreAction = () -> {
            layoutsByMode.clear();
            layoutsByMode.putAll(layoutState.modes());
            layoutInitialized = false;
            activeViewMode = null;

            IDEViewMode targetMode = layoutState.currentViewMode();
            if (viewModeController.getCurrentViewMode() == targetMode) {
                activateViewMode(targetMode);
            } else {
                viewModeController.setCurrentViewMode(targetMode);
            }
        };

        if (Platform.isFxApplicationThread()) {
            restoreAction.run();
        } else {
            Platform.runLater(restoreAction);
        }
    }

    private DetachableTabPane createBottomPane() {
        var pane = new DetachableTabPane();
        pane.getTabs().addAll(
            createTab("bottom:console", "Console", new ConsolePane()),
            createTab("bottom:terminal", "Terminal", TerminalFactory.create(project.getPath()))
        );

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

            rightPane.getTabs().add(createTab("right:gradle", "Gradle", new GradleToolsPane(project)));
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
