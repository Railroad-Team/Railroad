package dev.railroadide.railroad.ide.ui;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.IDELayoutState;
import dev.railroadide.railroad.ide.IDEViewMode;
import dev.railroadide.railroad.ide.IDEViewModeController;
import dev.railroadide.railroad.ide.ui.setup.PaneIconBarFactory;
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
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class IDEPane extends RRBorderPane implements AutoCloseable, IDEWorkspaceActions {
    private final Project project;
    private final IDEPaneLifecycle lifecycle;
    private final IDEViewModeController viewModeController;
    private final IDEContentRouter contentRouter;
    private final Map<IDEViewMode, List<Tab>> dockTabsByMode = new EnumMap<>(IDEViewMode.class);
    private final Map<IDEViewMode, DetachableTabPane> editorPanesByMode = new EnumMap<>(IDEViewMode.class);
    private final Map<IDEViewMode, IDELayoutState.ModeLayout> layoutsByMode = new EnumMap<>(IDEViewMode.class);
    private final Map<IDEViewMode, WeakReference<Node>> focusOwnersByMode = new EnumMap<>(IDEViewMode.class);
    private final Set<Tab> ownedTabs = Collections.newSetFromMap(new IdentityHashMap<>());

    private final DetachableTabPane leftPane;
    private final DetachableTabPane rightPane;
    private final DetachableTabPane bottomPane;
    private final SplitPane centerBottomSplit;
    private final SplitPane mainSplit;

    private IDEViewMode activeViewMode;
    private IDEViewMode pendingViewModeAfterGitDetection;
    private boolean layoutInitialized;
    private boolean layoutTransitioning;

    public IDEPane(Project project) {
        this.project = Objects.requireNonNull(project, "Project cannot be null");
        this.lifecycle = new IDEPaneLifecycle(this);
        this.viewModeController = new IDEViewModeController(
            Services.IDE_STATE.currentViewModeProperty(),
            mode -> mode != IDEViewMode.GIT || project.getGitManager().isActive()
        );
        this.lifecycle.onDispose(viewModeController::close);
        this.viewModeController.requestViewMode(IDEViewMode.CODE);

        setTop(new IDETopBarPane(project, viewModeController, this::requestViewMode, this));

        this.leftPane = createLeftPane();
        this.rightPane = new DetachableTabPane();
        trackOwnedTabs(rightPane);
        assignWhileAttached(UIIds.IDE.IDE_RIGHT_DOCK, rightPane);
        var codeEditorPane = getOrCreateEditorPane(editorPanesByMode, IDEViewMode.CODE);
        this.contentRouter = new IDEContentRouter(this);
        this.bottomPane = createBottomPane();

        this.centerBottomSplit = new SplitPane(codeEditorPane, bottomPane);
        centerBottomSplit.setOrientation(Orientation.VERTICAL);
        centerBottomSplit.setDividerPositions(0.75);

        this.mainSplit = new SplitPane(leftPane, centerBottomSplit);
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        mainSplit.setDividerPositions(0.15);
        setCenter(mainSplit);

        configureGradlePane(rightPane, mainSplit);
        setLeft(createLeftIconBar(leftPane, mainSplit));
        setBottom(createBottomBar(bottomPane, centerBottomSplit));
        installLayoutTracking();
        installFocusTracking();
        installGitAvailabilityTracking();

        viewModeController.onViewModeChanged(this::activateViewMode);

        KeybindHandler.registerCapture(KeybindContexts.of("railroad:ide"), this);

        assignWhileAttached(UIIds.IDE.IDE, this);
        lifecycle.onDispose(this::disposeOwnedContent);
    }

    private DetachableTabPane createLeftPane() {
        var pane = new DetachableTabPane();
        trackOwnedTabs(pane);
        var projectTab = createDockTab(IDEDockItem.PROJECT, IDEDockItem.DockPosition.LEFT);
        dockTabsByMode.put(IDEViewMode.CODE, List.of(projectTab));

        assignWhileAttached(UIIds.IDE.IDE_LEFT_DOCK, pane);
        return pane;
    }

    private List<Tab> createGitDockTabs() {
        return List.of(
            createDockTab(IDEDockItem.GIT_OVERVIEW, IDEDockItem.DockPosition.LEFT),
            createDockTab(IDEDockItem.GIT_COMMIT, IDEDockItem.DockPosition.LEFT),
            createDockTab(IDEDockItem.GIT_COMMIT_LIST, IDEDockItem.DockPosition.LEFT),
            createDockTab(IDEDockItem.GIT_BRANCHES, IDEDockItem.DockPosition.LEFT),
            createDockTab(IDEDockItem.GIT_REMOTES, IDEDockItem.DockPosition.LEFT),
            createDockTab(IDEDockItem.GIT_SYNC, IDEDockItem.DockPosition.LEFT),
            createDockTab(IDEDockItem.GIT_STASH, IDEDockItem.DockPosition.LEFT)
        );
    }

    private DetachableTabPane createCodeEditorPane() {
        var pane = new DetachableTabPane();
        trackOwnedTabs(pane);
        pane.getTabs().add(createTab("editor:welcome", "Welcome", new IDEWelcomePane()));
        trackSelectedTab(pane);

        assignWhileIDEAttached(UIIds.IDE.IDE_CODE_EDITOR_DOCK, pane);
        return pane;
    }

    private DetachableTabPane createGitEditorPane() {
        var pane = new DetachableTabPane();
        trackOwnedTabs(pane);
        pane.getTabs().add(createTab("editor:git-welcome", "Welcome", new IDEWelcomePane()));
        trackSelectedTab(pane);

        assignWhileIDEAttached(UIIds.IDE.IDE_GIT_EDITOR_DOCK, pane);
        return pane;
    }

    IDEContentRouter getContentRouter() {
        return contentRouter;
    }

    /**
     * Requests a user-facing view-mode change, respecting mode availability.
     *
     * @param viewMode requested mode
     * @return whether the request was accepted
     */
    public boolean requestViewMode(IDEViewMode viewMode) {
        return requestViewMode(viewMode, true);
    }

    private boolean requestViewMode(IDEViewMode viewMode, boolean clearPendingMode) {
        boolean accepted = viewModeController.requestViewMode(viewMode);
        if (accepted && clearPendingMode) {
            pendingViewModeAfterGitDetection = null;
        }
        return accepted;
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

    private IDEDockTab createDockTab(IDEDockItem dockItem, IDEDockItem.DockPosition dockPosition) {
        if (dockItem.preferredDockPosition() != dockPosition)
            throw new IllegalArgumentException(
                "Dock item '" + dockItem.id() + "' belongs in the " + dockItem.preferredDockPosition() + " dock"
            );
        return new IDEDockTab(dockItem, project);
    }

    private void activateViewMode(IDEViewMode viewMode) {
        IDEViewMode resolvedMode = viewMode == null ? IDEViewMode.CODE : viewMode;
        if (layoutInitialized && activeViewMode != null && activeViewMode != resolvedMode) {
            layoutsByMode.put(activeViewMode, captureModeLayout(activeViewMode));
        }

        layoutTransitioning = true;
        try {
            if (resolvedMode == IDEViewMode.GIT) {
                dockTabsByMode.computeIfAbsent(IDEViewMode.GIT, _ -> createGitDockTabs());
            }

            DetachableTabPane editorPane = getOrCreateEditorPane(editorPanesByMode, resolvedMode);
            leftPane.getTabs().setAll(dockTabsByMode.getOrDefault(resolvedMode, dockTabsByMode.get(IDEViewMode.CODE)));
            replaceEditorPane(editorPane);

            activeViewMode = resolvedMode;
            restoreModeLayout(layoutsByMode.getOrDefault(resolvedMode, IDELayoutState.ModeLayout.defaults()), editorPane);
            layoutInitialized = true;
            layoutsByMode.put(resolvedMode, captureModeLayout(resolvedMode));
            restoreModeFocus(resolvedMode, editorPane);
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
        boolean rightDockPresent = mainSplit.getItems().contains(rightPane);
        boolean rightVisible = rightPane.getTabs().isEmpty() ? previous.rightDockVisible() : rightDockPresent;
        boolean bottomVisible = centerBottomSplit.getItems().contains(bottomPane);

        double leftDivider = previous.leftDividerPosition();
        double rightDivider = previous.rightDividerPosition();
        double bottomDivider = previous.bottomDividerPosition();
        double[] mainDividers = mainSplit.getDividerPositions();
        if (leftVisible && rightDockPresent && mainDividers.length >= 2) {
            leftDivider = mainDividers[0];
            rightDivider = mainDividers[1];
        } else if (leftVisible && mainDividers.length >= 1) {
            leftDivider = mainDividers[0];
        } else if (rightDockPresent && mainDividers.length >= 1) {
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
        setDockVisible(mainSplit, rightPane, layout.rightDockVisible() && !rightPane.getTabs().isEmpty(), 2);
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

    private void installFocusTracking() {
        ChangeListener<Node> focusOwnerListener = (_, _, focusOwner) -> rememberModeFocus(focusOwner);
        ChangeListener<Scene> sceneListener = (_, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.focusOwnerProperty().removeListener(focusOwnerListener);
            }
            if (newScene != null) {
                newScene.focusOwnerProperty().addListener(focusOwnerListener);
                rememberModeFocus(newScene.getFocusOwner());
            }
        };

        sceneProperty().addListener(sceneListener);
        if (getScene() != null) {
            getScene().focusOwnerProperty().addListener(focusOwnerListener);
        }
        lifecycle.onDispose(() -> {
            sceneProperty().removeListener(sceneListener);
            if (getScene() != null) {
                getScene().focusOwnerProperty().removeListener(focusOwnerListener);
            }
            focusOwnersByMode.clear();
        });
    }

    private void installGitAvailabilityTracking() {
        ChangeListener<Boolean> listener = (_, _, available) -> {
            if (available) {
                if (pendingViewModeAfterGitDetection == IDEViewMode.GIT) {
                    pendingViewModeAfterGitDetection = null;
                    requestViewMode(IDEViewMode.GIT, false);
                }
            } else if (viewModeController.getCurrentViewMode() == IDEViewMode.GIT) {
                requestViewMode(IDEViewMode.CODE, false);
            }
        };
        project.getGitManager().activeProperty().addListener(listener);
        lifecycle.onDispose(() -> project.getGitManager().activeProperty().removeListener(listener));
    }

    private void rememberModeFocus(Node focusOwner) {
        if (focusOwner == null || activeViewMode == null || !isModeContent(focusOwner, activeViewMode))
            return;

        focusOwnersByMode.put(activeViewMode, new WeakReference<>(focusOwner));
    }

    private boolean isModeContent(Node node, IDEViewMode viewMode) {
        return isDescendantOf(node, leftPane)
            || isDescendantOf(node, editorPanesByMode.get(viewMode))
            || isDescendantOf(node, rightPane)
            || isDescendantOf(node, bottomPane);
    }

    private static boolean isDescendantOf(Node node, Node ancestor) {
        if (ancestor == null)
            return false;

        for (Node current = node; current != null; current = current.getParent()) {
            if (current == ancestor)
                return true;
        }

        return false;
    }

    private void restoreModeFocus(IDEViewMode viewMode, DetachableTabPane editorPane) {
        Platform.runLater(() -> {
            if (activeViewMode != viewMode || getScene() == null)
                return;

            WeakReference<Node> focusReference = focusOwnersByMode.get(viewMode);
            Node focusOwner = focusReference == null ? null : focusReference.get();
            if (focusOwner != null && focusOwner.getScene() == getScene() && focusOwner.isVisible() && !focusOwner.isDisabled()) {
                focusOwner.requestFocus();
                return;
            }

            Tab selectedTab = editorPane.getSelectionModel().getSelectedItem();
            Node selectedContent = selectedTab == null ? null : selectedTab.getContent();
            Objects.requireNonNullElse(selectedContent, editorPane).requestFocus();
        });
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

    @Override
    public boolean isDockItemAvailable(IDEDockItem dockItem) {
        Objects.requireNonNull(dockItem, "Dock item cannot be null");
        if (dockItem.owningViewMode() == IDEViewMode.GIT && !project.getGitManager().isActive()) {
            return false;
        }
        if (dockItem == IDEDockItem.GRADLE) {
            return findDockTab(rightPane, dockItem) != null;
        }
        return true;
    }

    @Override
    public boolean isDockItemActive(IDEDockItem dockItem) {
        Objects.requireNonNull(dockItem, "Dock item cannot be null");
        if (dockItem.owningViewMode() != null && dockItem.owningViewMode() != activeViewMode) {
            return false;
        }

        DetachableTabPane dockPane = dockPane(dockItem.preferredDockPosition());
        Tab dockTab = findDockTab(dockPane, dockItem);
        return dockTab != null
            && splitPane(dockItem.preferredDockPosition()).getItems().contains(dockPane)
            && dockPane.getSelectionModel().getSelectedItem() == dockTab;
    }

    @Override
    public void toggleDockItem(IDEDockItem dockItem) {
        Objects.requireNonNull(dockItem, "Dock item cannot be null");
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> toggleDockItem(dockItem));
            return;
        }
        if (!isDockItemAvailable(dockItem)) {
            return;
        }

        IDEViewMode owningMode = dockItem.owningViewMode();
        if (owningMode != null && owningMode != activeViewMode && !requestViewMode(owningMode)) {
            return;
        }

        DetachableTabPane dockPane = dockPane(dockItem.preferredDockPosition());
        Tab dockTab = findDockTab(dockPane, dockItem);
        if (dockTab == null) {
            return;
        }

        SplitPane splitPane = splitPane(dockItem.preferredDockPosition());
        if (isDockItemActive(dockItem)) {
            splitPane.getItems().remove(dockPane);
        } else {
            setDockVisible(splitPane, dockPane, true, preferredDockIndex(dockItem.preferredDockPosition()));
            dockPane.getSelectionModel().select(dockTab);
            IDELayoutState.ModeLayout layout = layoutsByMode.getOrDefault(activeViewMode, IDELayoutState.ModeLayout.defaults());
            if (dockItem.preferredDockPosition() == IDEDockItem.DockPosition.BOTTOM) {
                centerBottomSplit.setDividerPositions(clampDivider(layout.bottomDividerPosition(), 0.75));
            } else {
                restoreMainDividerPositions(layout);
            }
        }
        snapshotActiveLayout();
    }

    @Override
    public void resetCurrentLayout() {
        runOnApplicationThread(() -> resetLayouts(false));
    }

    @Override
    public void resetAllLayouts() {
        runOnApplicationThread(() -> resetLayouts(true));
    }

    private void resetLayouts(boolean allModes) {
        if (activeViewMode == null) {
            return;
        }

        layoutTransitioning = true;
        try {
            if (allModes) {
                layoutsByMode.clear();
                focusOwnersByMode.clear();
            } else {
                layoutsByMode.remove(activeViewMode);
                focusOwnersByMode.remove(activeViewMode);
            }

            IDELayoutState.ModeLayout defaults = IDELayoutState.ModeLayout.defaults();
            restoreModeLayout(defaults, getOrCreateEditorPane(editorPanesByMode, activeViewMode));
            layoutsByMode.put(activeViewMode, captureModeLayout(activeViewMode));
        } finally {
            layoutTransitioning = false;
        }
    }

    private DetachableTabPane dockPane(IDEDockItem.DockPosition position) {
        return switch (position) {
            case LEFT -> leftPane;
            case RIGHT -> rightPane;
            case BOTTOM -> bottomPane;
        };
    }

    private SplitPane splitPane(IDEDockItem.DockPosition position) {
        return position == IDEDockItem.DockPosition.BOTTOM ? centerBottomSplit : mainSplit;
    }

    private static int preferredDockIndex(IDEDockItem.DockPosition position) {
        return switch (position) {
            case LEFT -> 0;
            case RIGHT -> 2;
            case BOTTOM -> 1;
        };
    }

    private static Tab findDockTab(DetachableTabPane pane, IDEDockItem dockItem) {
        return pane.getTabs().stream()
            .filter(tab -> dockItem.id().equals(tab.getId()))
            .findFirst()
            .orElse(null);
    }

    private static void runOnApplicationThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
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
            if (targetMode == IDEViewMode.GIT && !project.getGitManager().isActive()) {
                pendingViewModeAfterGitDetection = IDEViewMode.GIT;
                targetMode = IDEViewMode.CODE;
            } else {
                pendingViewModeAfterGitDetection = null;
            }
            if (viewModeController.getCurrentViewMode() == targetMode) {
                activateViewMode(targetMode);
            } else {
                requestViewMode(targetMode, false);
            }
        };

        if (Platform.isFxApplicationThread()) {
            restoreAction.run();
        } else {
            Platform.runLater(restoreAction);
        }
    }

    @Override
    public void close() {
        lifecycle.close();
    }

    private void trackOwnedTabs(DetachableTabPane pane) {
        pane.getTabs().forEach(this::trackOwnedTab);
        ListChangeListener<Tab> listener = change -> {
            while (change.next()) {
                change.getAddedSubList().forEach(this::trackOwnedTab);
            }
        };
        pane.getTabs().addListener(listener);
        lifecycle.onDispose(() -> pane.getTabs().removeListener(listener));
    }

    private void trackOwnedTab(Tab tab) {
        if (!ownedTabs.add(tab))
            return;

        tab.addEventHandler(Tab.CLOSED_EVENT, _ -> {
            IDEContentDisposer.dispose(tab, Collections.newSetFromMap(new IdentityHashMap<>()));
            ownedTabs.remove(tab);
        });
    }

    private void disposeOwnedContent() {
        Set<Tab> tabsToDispose = new LinkedHashSet<>(ownedTabs);
        dockTabsByMode.values().forEach(tabsToDispose::addAll);
        editorPanesByMode.values().forEach(pane -> tabsToDispose.addAll(pane.getTabs()));
        tabsToDispose.addAll(leftPane.getTabs());
        tabsToDispose.addAll(rightPane.getTabs());
        tabsToDispose.addAll(bottomPane.getTabs());

        Set<Object> disposed = Collections.newSetFromMap(new IdentityHashMap<>());
        tabsToDispose.forEach(tab -> IDEContentDisposer.dispose(tab, disposed));
        IDEContentDisposer.dispose(getTop(), disposed);
        IDEContentDisposer.dispose(getLeft(), disposed);
        IDEContentDisposer.dispose(getRight(), disposed);
        IDEContentDisposer.dispose(getBottom(), disposed);
        IDEContentDisposer.dispose(getCenter(), disposed);
        ownedTabs.clear();
        dockTabsByMode.clear();
        editorPanesByMode.clear();
        layoutsByMode.clear();
        focusOwnersByMode.clear();
    }

    private DetachableTabPane createBottomPane() {
        var pane = new DetachableTabPane();
        trackOwnedTabs(pane);
        pane.getTabs().addAll(
            createDockTab(IDEDockItem.CONSOLE, IDEDockItem.DockPosition.BOTTOM),
            createDockTab(IDEDockItem.TERMINAL, IDEDockItem.DockPosition.BOTTOM)
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
                .anyMatch(tab -> IDEDockItem.GRADLE.id().equals(tab.getId()))) {
                return;
            }

            rightPane.getTabs().add(createDockTab(IDEDockItem.GRADLE, IDEDockItem.DockPosition.RIGHT));
            setRight(PaneIconBarFactory.create(
                rightPane,
                mainSplit,
                Orientation.VERTICAL,
                2
            ));

            if (activeViewMode != null && layoutsByMode
                .getOrDefault(activeViewMode, IDELayoutState.ModeLayout.defaults())
                .rightDockVisible()) {
                setDockVisible(mainSplit, rightPane, true, 2);
                restoreMainDividerPositions(layoutsByMode.getOrDefault(activeViewMode, IDELayoutState.ModeLayout.defaults()));
                snapshotActiveLayout();
            }
        });
    }

    private static Node createLeftIconBar(DetachableTabPane leftPane, SplitPane mainSplit) {
        return PaneIconBarFactory.create(
            leftPane,
            mainSplit,
            Orientation.VERTICAL,
            0
        );
    }

    private static RRVBox createBottomBar(DetachableTabPane consolePane, SplitPane centerBottomSplit) {
        var bottomBar = new RRVBox();
        var bottomIcons = PaneIconBarFactory.create(
            consolePane,
            centerBottomSplit,
            Orientation.HORIZONTAL,
            1
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
