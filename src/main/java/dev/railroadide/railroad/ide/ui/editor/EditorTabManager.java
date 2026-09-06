package dev.railroadide.railroad.ide.ui.editor;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.language.EditorOpenView;
import dev.railroadide.railroad.ide.language.LanguageSupport;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.ide.language.impl.ImageLanguageSupport;
import dev.railroadide.railroad.ide.language.impl.PlainTextLanguageSupport;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentIdentity;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.ui.*;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.plugin.defaults.FileSystemDocument;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.events.DocumentEvent;
import dev.railroadide.railroad.plugin.spi.events.DocumentRenamedEvent;
import dev.railroadide.railroad.plugin.spi.events.ProjectEvent;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.settings.keybinds.KeybindHandler;
import dev.railroadide.railroad.settings.keybinds.Keybinds;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.utility.FileUtils;
import dev.railroadide.railroad.utility.javafx.JavaFXUtils;
import dev.railroadide.railroad.window.DialogBuilder;
import dev.railroadide.railroad.window.WindowBoundsRestorer;
import dev.railroadide.railroad.window.WindowBuilder;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;

import static dev.railroadide.railroad.ide.ui.editor.EditorTabSessionState.DEFAULT_EDITOR_GROUP_ID;

/**
 * Coordinates editor tabs, preview replacement, saving, group layouts, and session restoration.
 */
@SuppressWarnings("resource")
public class EditorTabManager {
    private final Map<DocumentId, EditorTab> openTabs = new LinkedHashMap<>();
    private final Deque<ClosedEditorTab> recentlyClosedTabs = new ArrayDeque<>();
    private final Set<EditorTab> tabsByRecency = new LinkedHashSet<>();
    private final Map<Tab, EditorTab> tabsByControl = new IdentityHashMap<>();
    private final Map<DocumentId, ClosedEditorTab> pendingCloseSnapshots = new LinkedHashMap<>();
    private final Map<DetachableTabPane, ChangeListener<Tab>> selectionListeners = new IdentityHashMap<>();
    private final Map<DetachableTabPane, EventHandler<MouseEvent>> mouseKeybindHandlers = new IdentityHashMap<>();
    private final Map<DetachableTabPane, EditorTabStripSupport> tabStripSupport = new IdentityHashMap<>();
    private final Map<DetachableTabPane, String> editorGroupIds = new IdentityHashMap<>();
    private final Map<DetachableTabPane, ListChangeListener<Tab>> emptyGroupListeners = new IdentityHashMap<>();
    private final Map<DetachableTabPane, ListChangeListener<Tab>> tabOrderListeners = new IdentityHashMap<>();
    private final Set<DetachableTabPane> pendingTabOrderUpdates = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<SplitPane> editorSplitPanes = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Window> trackedDetachedWindows = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<EditorTab, Stage> failedCloseDialogs = new IdentityHashMap<>();
    private final Set<EditorTab> discardApprovedTabs = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean restoring;
    private boolean replacingPreview;
    private boolean selectionUpdateScheduled;
    private EditorTab pendingSelection;
    private DetachableTabPane primaryEditorPane;
    private long selectionGeneration;
    private long editorGroupSequence;

    /**
     * Reports editor tabs whose requested save did not succeed.
     *
     * @param failedTabs tabs whose save failed
     */
    public record SaveResult(List<EditorTab> failedTabs) {
        /**
         * Creates a save result with an immutable copy of the failed tabs.
         *
         * @param failedTabs tabs whose save failed
         */
        public SaveResult {
            failedTabs = List.copyOf(failedTabs);
        }

        /**
         * Reports whether all requested saves succeeded.
         *
         * @return true when no tabs were reported as failed
         */
        public boolean successful() {
            return failedTabs.isEmpty();
        }
    }

    private record TabOpenRequest(
        boolean activate,
        boolean openExternally,
        boolean applyPropertiesToExisting,
        boolean pinned,
        boolean preview,
        String editorGroupId,
        int insertionIndex
    ) {
        private static TabOpenRequest normal() {
            return new TabOpenRequest(
                true,
                true,
                false,
                false,
                false,
                DEFAULT_EDITOR_GROUP_ID,
                -1);
        }

        private static TabOpenRequest preview(String editorGroupId, int insertionIndex) {
            return new TabOpenRequest(
                true,
                false,
                false,
                false,
                true,
                editorGroupId,
                insertionIndex);
        }

        private static TabOpenRequest restored(EditorTabSessionState state) {
            return new TabOpenRequest(
                false,
                false,
                true,
                state.pinned(),
                state.preview(),
                state.editorGroupId(),
                -1);
        }

        private static TabOpenRequest reopened(ClosedEditorTab tab, boolean activate) {
            return new TabOpenRequest(
                activate,
                false,
                true,
                tab.pinned(),
                tab.preview(),
                tab.editorGroupId(),
                tab.previousIndex());
        }
    }

    /**
     * Creates a tab manager and subscribes to project, document, and tab-setting changes.
     */
    public EditorTabManager() {
        Railroad.EVENT_BUS.subscribe(ProjectEvent.class, this::handleProjectClosed);
        Railroad.EVENT_BUS.subscribe(DocumentRenamedEvent.class, this::handleRenamed);
        Railroad.EVENT_BUS.subscribe(DocumentEvent.class, this::handleDocumentEvent);
        Settings.EDITOR_TAB_LIMIT.addListener((_, _) -> JavaFXUtils.runOnApplicationThread(
            () -> enforceTabLimit(activeTab().orElse(null))));
        Settings.RECENTLY_CLOSED_TAB_LIMIT.addListener((_, newLimit) -> JavaFXUtils.runOnApplicationThread(
            () -> trimRecentlyClosedTabs(newLimit)));
        Settings.ENABLE_PREVIEW_TABS.addListener((_, enabled) -> {
            if (!Boolean.TRUE.equals(enabled)) {
                JavaFXUtils.runOnApplicationThread(() -> previewTab().ifPresent(this::promote));
            }
        });
        Settings.SYNCHRONIZE_PROJECT_EXPLORER_WITH_ACTIVE_TAB.addListener((_, enabled) -> {
            if (Boolean.TRUE.equals(enabled)) {
                JavaFXUtils.runOnApplicationThread(
                    () -> activeTab().ifPresent(this::revealInProjectExplorer));
            }
        });
    }

    /**
     * Registers a pane created by a drag split or detached-window operation.
     *
     * @param tabPane editor group tab pane
     */
    public void registerEditorPane(DetachableTabPane tabPane) {
        Objects.requireNonNull(tabPane, "Tab pane cannot be null");
        JavaFXUtils.runOnApplicationThread(() -> registerEditorPaneOnApplicationThread(tabPane));
    }

    private void registerEditorPaneOnApplicationThread(DetachableTabPane tabPane) {
        String groupId = editorGroupIds.get(tabPane);
        if (groupId == null) {
            groupId = nextEditorGroupId();
        }
        ensureSelectionListener(tabPane, groupId);
        trackEmptySplitGroup(tabPane);
        reconcilePaneMembership(tabPane);
        Platform.runLater(() -> {
            registerEditorSplitAncestors(tabPane);
            trackDetachedWindow(tabPane);
        });
    }

    /**
     * Opens a file through the active project's language support, selecting an existing tab when possible.
     *
     * @param path path of the document file
     */
    public void open(Path path) {
        Objects.requireNonNull(path, "Path cannot be null");
        if (Files.isDirectory(path))
            return;

        Project project = Services.IDE_STATE.getCurrentProject();
        if (project == null)
            throw new IllegalStateException("Cannot open a file without an active project");

        IDEContentRouter.routeActive(WorkspaceContentTargets.CODE_EDITOR,
            tabPane -> openInTabPane(project, path, tabPane, TabOpenRequest.normal()));
    }

    /**
     * Opens a path in the reusable preview slot. When preview tabs are disabled this
     * behaves like a normal open. Existing permanent tabs are only selected; they are
     * never demoted back into previews.
     *
     * @param path path of the document file
     */
    public void openPreview(Path path) {
        Objects.requireNonNull(path, "Path cannot be null");
        if (Files.isDirectory(path))
            return;

        if (!Boolean.TRUE.equals(Settings.ENABLE_PREVIEW_TABS.getValue())) {
            open(path);
            return;
        }

        Project project = Services.IDE_STATE.getCurrentProject();
        if (project == null)
            throw new IllegalStateException("Cannot preview a file without an active project");

        IDEContentRouter.routeActive(WorkspaceContentTargets.CODE_EDITOR,
            fallbackPane -> openPreviewInTabPane(project, path, fallbackPane));
    }

    private EditorTab openPreviewInTabPane(
        Project project,
        Path path,
        DetachableTabPane fallbackPane
    ) {
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedPath))
            return null;

        EditorTab existingTab = findOpen(normalizedPath).orElse(null);
        if (existingTab != null) {
            DetachableTabPane targetPane = editorPaneFor(existingTab, fallbackPane);
            return openInTabPane(
                project,
                normalizedPath,
                targetPane,
                TabOpenRequest.preview(ensureEditorGroupId(targetPane), -1));
        }

        // Do not evict a useful preview for a path Railroad cannot render.
        if (resolveLanguageSupport(normalizedPath) == null)
            return null;

        EditorTab currentPreview = previewTab().orElse(null);
        DetachableTabPane targetPane = activeEditorPane(fallbackPane);
        int insertionIndex = -1;
        if (currentPreview != null) {
            if (currentPreview.dirty() || currentPreview.pinned()) {
                promote(currentPreview);
            } else {
                targetPane = editorPaneFor(currentPreview, targetPane);
                insertionIndex = tabIndex(currentPreview);
                replacingPreview = true;
                if (!requestClose(currentPreview)) {
                    replacingPreview = false;
                    return null;
                }
            }
        }

        try {
            String groupId = ensureEditorGroupId(targetPane);
            return openInTabPane(
                project,
                normalizedPath,
                targetPane,
                TabOpenRequest.preview(groupId, insertionIndex));
        } finally {
            replacingPreview = false;
            if (openTabs.isEmpty()) {
                restoreEmptyEditorState();
            }
        }
    }

    private DetachableTabPane activeEditorPane(DetachableTabPane fallbackPane) {
        return activeTab()
            .map(EditorTab::tab)
            .map(Tab::getTabPane)
            .filter(DetachableTabPane.class::isInstance)
            .map(DetachableTabPane.class::cast)
            .orElse(fallbackPane);
    }

    private static DetachableTabPane editorPaneFor(EditorTab tab, DetachableTabPane fallbackPane) {
        return tab.tab().getTabPane() instanceof DetachableTabPane tabPane ? tabPane : fallbackPane;
    }

    /**
     * Moves the editor tab into a detached editor window.
     *
     * @param tab editor tab to act on
     */
    public void openInNewWindow(EditorTab tab) {
        Objects.requireNonNull(tab, "Tab cannot be null");
        if (openTabs.get(tab.documentId()) != tab)
            return;

        if (Services.IDE_STATE.getCurrentProject() == null)
            throw new IllegalStateException("Cannot open a file without an active project");

        JavaFXUtils.runOnApplicationThread(() -> detachIntoNewWindow(tab));
    }

    private void detachIntoNewWindow(EditorTab editorTab) {
        if (openTabs.get(editorTab.documentId()) != editorTab)
            return;
        if (!(editorTab.tab().getTabPane() instanceof DetachableTabPane sourceTabPane))
            return;

        Tab tab = editorTab.tab();
        int previousIndex = sourceTabPane.getTabs().indexOf(tab);
        if (previousIndex < 0)
            return;

        sourceTabPane.getTabs().remove(previousIndex);
        try {
            Stage stage = sourceTabPane.getStageFactory().createStage(sourceTabPane, tab);
            if (tab.getTabPane() instanceof DetachableTabPane detachedTabPane) {
                ensureSelectionListener(detachedTabPane, editorTab.editorGroupId());
                stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, _ -> removeSelectionListener(detachedTabPane));
                queueSelectionUpdate(tab);
            }
            activate(editorTab);
            Railroad.WINDOW_MANAGER.registerChildWindow(stage);
        } catch (RuntimeException exception) {
            if (tab.getTabPane() == null) {
                sourceTabPane.getTabs().add(Math.min(previousIndex, sourceTabPane.getTabs().size()), tab);
                sourceTabPane.getSelectionModel().select(tab);
            }
            Railroad.LOGGER.error("Failed to open editor tab {} in a new window", editorTab.path(), exception);
        }
    }

    /**
     * Restores file paths as legacy tabs and selects the requested active path.
     *
     * @param paths document paths to open in their saved order
     * @param activePath document path to activate after restoration
     */
    public void restore(Iterable<Path> paths, Path activePath) {
        Objects.requireNonNull(paths, "Paths cannot be null");
        var legacyState = new ArrayList<EditorTabSessionState>();
        int order = 0;
        for (Path path : paths) {
            if (path != null) {
                legacyState.add(EditorTabSessionState.legacy(path, order++, pathsMatch(path, activePath)));
            }
        }
        restoreSession(legacyState);
    }

    /**
     * Restores tab states using the default workspace layout.
     *
     * @param sessionState saved tab states to restore
     */
    public void restoreSession(List<EditorTabSessionState> sessionState) {
        Objects.requireNonNull(sessionState, "Session state cannot be null");
        restoreWorkspaceSession(EditorWorkspaceSessionState.legacy(sessionState));
    }

    /**
     * Restores editor layouts, detached windows, and saved tabs for the active project.
     *
     * @param workspaceState saved layout, windows, and tab states to restore
     */
    public void restoreWorkspaceSession(EditorWorkspaceSessionState workspaceState) {
        Objects.requireNonNull(workspaceState, "Editor workspace state cannot be null");
        if (!workspaceState.isSupported()) {
            restoreSession(workspaceState.tabs());
            return;
        }

        Project project = Services.IDE_STATE.getCurrentProject();
        if (project == null)
            return;

        List<EditorTabSessionState> tabsToRestore = workspaceState.tabs().stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingInt(EditorTabSessionState::order))
            .toList();
        IDEContentRouter.routeActive(WorkspaceContentTargets.CODE_EDITOR, tabPane -> {
            IDEPane idePane = Services.UI_MANAGER.lookup(UIIds.IDE.IDE).orElse(null);
            restoreWorkspaceOnApplicationThread(project, idePane, tabPane, workspaceState, tabsToRestore);
        });
    }

    private EditorTab openInTabPane(
        Project project,
        Path path,
        DetachableTabPane tabPane,
        TabOpenRequest request
    ) {
        if (primaryEditorPane == null && DEFAULT_EDITOR_GROUP_ID.equals(request.editorGroupId())) {
            primaryEditorPane = tabPane;
        }
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedPath)) {
            Railroad.LOGGER.warn("Cannot open missing or non-file path: {}", normalizedPath);
            return null;
        }

        EditorTab existingTab = findOpen(normalizedPath).orElse(null);
        if (existingTab != null) {
            if (request.applyPropertiesToExisting()) {
                existingTab.setPinned(request.pinned());
                setPreview(existingTab, request.preview()
                    && !request.pinned()
                    && Boolean.TRUE.equals(Settings.ENABLE_PREVIEW_TABS.getValue()));
                existingTab.setEditorGroupId(request.editorGroupId());
            } else if (!request.preview()) {
                promote(existingTab);
            }
            reattachExistingTabIfNeeded(existingTab, tabPane);
            if (request.activate()) {
                select(existingTab, tabPane);
                activate(existingTab);
            }
            return existingTab;
        }

        LanguageSupport support = resolveLanguageSupport(normalizedPath);
        if (support == null) {
            if (request.openExternally()) {
                FileUtils.openInDefaultApplication(normalizedPath);
            }
            return null;
        }

        var editorOpenView = support.open(project, normalizedPath);
        if (editorOpenView == null) {
            if (request.openExternally()) {
                FileUtils.openInDefaultApplication(normalizedPath);
            }
            return null;
        }

        var document = new FileSystemDocument(normalizedPath, support.languageId());
        DocumentIdentity identity = Services.IDE_STATE.identifyDocument(document);
        boolean preview = request.preview()
            && !request.pinned()
            && Boolean.TRUE.equals(Settings.ENABLE_PREVIEW_TABS.getValue());
        if (preview) {
            previewTab().ifPresent(this::promote);
        }
        var editorTab = new EditorTab(
            identity,
            document,
            editorOpenView,
            request.editorGroupId(),
            request.pinned(),
            preview);
        editorTab.tab().setId(normalizedPath.toString());
        editorTab.tab().addEventHandler(Tab.TAB_CLOSE_REQUEST_EVENT, event -> handleCloseRequest(editorTab, event));
        editorTab.tab().addEventHandler(Tab.CLOSED_EVENT, _ -> handleClosed(editorTab));

        openTabs.put(editorTab.documentId(), editorTab);
        tabsByControl.put(editorTab.tab(), editorTab);
        markRecentlyUsed(editorTab);
        refreshTabPresentations();
        editorTab.pinnedProperty().addListener((_, _, isPinned) -> {
            if (isPinned) {
                promote(editorTab);
            }
            keepPinnedTabsOnLeft(editorTab.tab().getTabPane());
            if (!isPinned) {
                scheduleTabLimitEnforcement();
            }
        });
        editorTab.dirtyProperty().addListener((_, wasDirty, isDirty) -> {
            if (isDirty) {
                promote(editorTab);
            }
            if (wasDirty && !isDirty) {
                scheduleTabLimitEnforcement();
            }
        });
        ensureSelectionListener(tabPane, request.editorGroupId());
        Services.IDE_STATE.openDocument(document);
        addToTabPane(tabPane, editorTab.tab(), request.insertionIndex());

        if (request.activate()) {
            tabPane.getSelectionModel().select(editorTab.tab());
            activate(editorTab);
        }
        if (!restoring) {
            enforceTabLimit(editorTab);
        }
        return editorTab;
    }

    private void restoreWorkspaceOnApplicationThread(
        Project project,
        IDEPane idePane,
        DetachableTabPane primaryPane,
        EditorWorkspaceSessionState workspaceState,
        List<EditorTabSessionState> tabsToRestore
    ) {
        restoring = true;
        try {
            primaryEditorPane = primaryPane;
            removeWelcomeTabs(primaryPane);
            editorSplitPanes.clear();

            Map<String, DetachableTabPane> groups = new LinkedHashMap<>();
            if (idePane != null) {
                idePane.detachEditorLayoutRoot(WorkspaceModes.CODE);
                detachFromParent(primaryPane);

                Deque<DetachableTabPane> reusablePanes = new ArrayDeque<>();
                reusablePanes.add(primaryPane);
                Node mainRoot = restoreLayoutNode(
                    workspaceState.mainLayout(), primaryPane, reusablePanes, groups);
                idePane.setEditorLayoutRoot(WorkspaceModes.CODE, mainRoot);
            } else {
                assignEditorGroup(primaryPane, DEFAULT_EDITOR_GROUP_ID);
                groups.put(DEFAULT_EDITOR_GROUP_ID, primaryPane);
            }

            Set<String> restorableGroupIds = tabsToRestore.stream()
                .filter(tab -> tab.path() != null && Files.isRegularFile(tab.path()))
                .map(EditorTabSessionState::editorGroupId)
                .collect(Collectors.toSet());

            var detachedLayouts = new ArrayList<Map.Entry<DetachedEditorWindowState, Map<String, DetachableTabPane>>>();
            if (idePane != null) {
                for (DetachedEditorWindowState windowState : workspaceState.detachedWindows()) {
                    Set<String> windowGroups = new LinkedHashSet<>();
                    collectGroupIds(windowState.layout(), windowGroups);
                    if (Collections.disjoint(windowGroups, restorableGroupIds))
                        continue;

                    Map<String, DetachableTabPane> windowPanes = new LinkedHashMap<>();
                    Node windowRoot = restoreLayoutNode(
                        windowState.layout(), primaryPane, new ArrayDeque<>(), windowPanes);
                    groups.putAll(windowPanes);
                    Stage stage = idePane.createDetachedEditorStage(windowRoot);
                    WindowBoundsRestorer.restore(
                        stage,
                        windowState.x(),
                        windowState.y(),
                        windowState.width(),
                        windowState.height(),
                        windowState.maximized());
                    stage.show();
                    Railroad.WINDOW_MANAGER.registerChildWindow(stage);
                    collectEditorPanes(windowRoot).forEach(this::trackDetachedWindow);
                    detachedLayouts.add(Map.entry(windowState, windowPanes));
                }
            }

            Map<String, EditorTab> restoredTabs = new LinkedHashMap<>();
            DetachableTabPane fallbackPane = groups.values().stream().findFirst().orElse(primaryPane);
            for (EditorTabSessionState tabState : tabsToRestore) {
                try {
                    Services.IDE_STATE.restoreDocumentIdentity(tabState.identity());
                    if (tabState.path() == null) {
                        Railroad.LOGGER.warn("No editor provider can restore virtual document {}",
                            tabState.identity().uri());
                        continue;
                    }
                    DetachableTabPane destination = groups.getOrDefault(tabState.editorGroupId(), fallbackPane);
                    EditorTab restoredTab = openInTabPane(
                        project,
                        tabState.path(),
                        destination,
                        TabOpenRequest.restored(tabState));
                    if (restoredTab != null) {
                        restoredTabs.put(restoredTab.documentId().toString(), restoredTab);
                        restoreViewState(restoredTab, tabState.viewState());
                    }
                } catch (RuntimeException exception) {
                    Railroad.LOGGER.error("Failed to restore editor tab for {}", tabState.path(), exception);
                }
            }

            restoreGroupSelections(workspaceState.mainLayout(), groups, restoredTabs);
            detachedLayouts.forEach(entry -> restoreGroupSelections(
                entry.getKey().layout(), entry.getValue(), restoredTabs));

            EditorTab activeTab = tabsToRestore.stream()
                .filter(EditorTabSessionState::active)
                .map(state -> state.identity().id().toString())
                .map(restoredTabs::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> restoredTabs.values().stream().findFirst().orElse(null));
            if (activeTab != null && activeTab.tab().getTabPane() != null) {
                activeTab.tab().getTabPane().getSelectionModel().select(activeTab.tab());
            }
            activate(activeTab);
        } finally {
            restoring = false;
        }
        enforceTabLimit(activeTab().orElse(null));
        cleanupEmptyEditorGroups();
        if (openTabs.isEmpty() && !replacingPreview) {
            restoreEmptyEditorState();
        }
    }

    private Node restoreLayoutNode(
        EditorLayoutNodeState state,
        DetachableTabPane template,
        Deque<DetachableTabPane> reusablePanes,
        Map<String, DetachableTabPane> groups
    ) {
        if (state == null || state.group()) {
            DetachableTabPane pane = reusablePanes.pollFirst();
            if (pane == null) {
                pane = createSiblingTabPane(template);
            }
            String requestedGroupId = state == null ? DEFAULT_EDITOR_GROUP_ID : state.groupId();
            String groupId = groups.containsKey(requestedGroupId) ? nextEditorGroupId() : requestedGroupId;
            assignEditorGroup(pane, groupId);
            groups.put(groupId, pane);
            return pane;
        }

        List<Node> children = state.children().stream()
            .map(child -> restoreLayoutNode(child, template, reusablePanes, groups))
            .toList();
        if (children.size() == 1)
            return children.getFirst();

        var splitPane = new SplitPane();
        splitPane.setOrientation(state.orientation());
        splitPane.getItems().addAll(children);
        applyDividerPositions(splitPane, state.dividerPositions());
        editorSplitPanes.add(splitPane);
        return splitPane;
    }

    private void assignEditorGroup(DetachableTabPane pane, String groupId) {
        editorGroupIds.put(pane, groupId);
        pane.setCloseIfEmpty(false);
        ensureSelectionListener(pane, groupId);
        trackEmptySplitGroup(pane);
        reconcilePaneMembership(pane);
    }

    private static void applyDividerPositions(SplitPane splitPane, List<Double> savedPositions) {
        int dividerCount = Math.max(0, splitPane.getItems().size() - 1);
        if (dividerCount == 0)
            return;

        double[] positions = new double[dividerCount];
        for (int index = 0; index < dividerCount; index++) {
            positions[index] = index < savedPositions.size()
                ? savedPositions.get(index)
                : (double) (index + 1) / (dividerCount + 1);
        }
        splitPane.setDividerPositions(positions);
    }

    private static void collectGroupIds(EditorLayoutNodeState state, Set<String> groupIds) {
        if (state.group()) {
            groupIds.add(state.groupId());
            return;
        }
        state.children().forEach(child -> collectGroupIds(child, groupIds));
    }

    private static void restoreGroupSelections(
        EditorLayoutNodeState state,
        Map<String, DetachableTabPane> groups,
        Map<String, EditorTab> restoredTabs
    ) {
        if (state.group()) {
            DetachableTabPane pane = groups.get(state.groupId());
            EditorTab selected = restoredTabs.get(state.selectedDocumentId());
            if (pane != null) {
                if (selected != null && selected.tab().getTabPane() == pane) {
                    pane.getSelectionModel().select(selected.tab());
                } else if (!pane.getTabs().isEmpty()) {
                    pane.getSelectionModel().selectFirst();
                }
            }
            return;
        }
        state.children().forEach(child -> restoreGroupSelections(child, groups, restoredTabs));
    }

    private static void removeWelcomeTabs(DetachableTabPane pane) {
        List.copyOf(pane.getTabs()).stream()
            .filter(tab -> tab.getContent() instanceof IDEWelcomePane)
            .forEach(IDETabLifecycle::requestClose);
    }

    private static void detachFromParent(Node node) {
        Parent parent = node.getParent();
        if (parent instanceof SplitPane splitPane) {
            splitPane.getItems().remove(node);
        } else if (parent instanceof Pane pane) {
            pane.getChildren().remove(node);
        }
    }

    private void reattachExistingTabIfNeeded(EditorTab editorTab, DetachableTabPane targetTabPane) {
        Tab tab = editorTab.tab();
        TabPane currentTabPane = tab.getTabPane();
        if (currentTabPane != null && !shouldReattach(currentTabPane, targetTabPane))
            return;

        if (currentTabPane != null) {
            currentTabPane.getTabs().remove(tab);
        }
        String targetGroupId = ensureEditorGroupId(targetTabPane);
        ensureSelectionListener(targetTabPane, targetGroupId);
        addToTabPane(targetTabPane, tab, -1);
        editorTab.setEditorGroupId(targetGroupId);
    }

    private static boolean shouldReattach(TabPane currentTabPane, TabPane targetTabPane) {
        if (currentTabPane == targetTabPane)
            return false;
        if (targetTabPane.getScene() == null
            || targetTabPane.getScene().getWindow() == null
            || !targetTabPane.getScene().getWindow().isShowing())
            return false;

        return currentTabPane.getScene() == null
            || currentTabPane.getScene().getWindow() == null
            || !currentTabPane.getScene().getWindow().isShowing();
    }

    private static void select(EditorTab editorTab, DetachableTabPane fallbackTabPane) {
        TabPane owningTabPane = editorTab.tab().getTabPane();
        if (owningTabPane == null) {
            fallbackTabPane.getSelectionModel().select(editorTab.tab());
        } else {
            owningTabPane.getSelectionModel().select(editorTab.tab());
        }
    }

    private static LanguageSupport resolveLanguageSupport(Path path) {
        return LanguageSupportRegistry.find(path)
            .orElseGet(() -> FileUtils.isBinaryFile(path)
                ? (FileUtils.isImageFile(path) ? ImageLanguageSupport.INSTANCE : null)
                : PlainTextLanguageSupport.INSTANCE);
    }

    private void addToTabPane(DetachableTabPane tabPane, Tab tab, int insertionIndex) {
        int welcomeIndex = -1;
        for (int index = 0; index < tabPane.getTabs().size(); index++) {
            if (tabPane.getTabs().get(index).getContent() instanceof IDEWelcomePane) {
                welcomeIndex = index;
                break;
            }
        }

        if (welcomeIndex >= 0) {
            Tab welcomeTab = tabPane.getTabs().get(welcomeIndex);
            IDETabLifecycle.requestClose(welcomeTab);
            tabPane.getTabs().add(Math.min(welcomeIndex, tabPane.getTabs().size()), tab);
        } else {
            int targetIndex = insertionIndex < 0
                ? tabPane.getTabs().size()
                : Math.min(insertionIndex, tabPane.getTabs().size());
            tabPane.getTabs().add(targetIndex, tab);
        }
        keepPinnedTabsOnLeft(tabPane);
    }

    private void ensureSelectionListener(DetachableTabPane tabPane, String editorGroupId) {
        editorGroupIds.putIfAbsent(tabPane, editorGroupId);
        ensureTabOrderListener(tabPane);
        ensureMouseKeybindHandler(tabPane);
        tabStripSupport.computeIfAbsent(tabPane, pane -> new EditorTabStripSupport(
            pane,
            tabsByControl::get,
            this::cleanupEmptyEditorGroups));
        if (selectionListeners.containsKey(tabPane))
            return;

        ChangeListener<Tab> listener = (_, _, selectedTab) -> queueSelectionUpdate(selectedTab);
        tabPane.getSelectionModel().selectedItemProperty().addListener(listener);
        selectionListeners.put(tabPane, listener);
    }

    private void ensureMouseKeybindHandler(DetachableTabPane tabPane) {
        if (mouseKeybindHandlers.containsKey(tabPane))
            return;

        EventHandler<MouseEvent> handler = event -> {
            Node tabHeader = findTabHeaderAtEventTarget(tabPane, event);
            if (tabHeader == null)
                return;

            if (event.getButton() == MouseButton.PRIMARY
                && event.getClickCount() == 2) {
                EditorTab editorTab = getTabAt(tabHeader);
                if (editorTab != null) {
                    promote(editorTab);
                }
            }

            if (KeybindHandler.dispatchMouseEvent(Keybinds.EDITOR_TABS, event, tabHeader)) {
                event.consume();
            }
        };
        tabPane.addEventFilter(MouseEvent.MOUSE_CLICKED, handler);
        mouseKeybindHandlers.put(tabPane, handler);
    }

    private Node findTabHeaderAtEventTarget(TabPane tabPane, MouseEvent event) {
        for (Node current = event.getPickResult().getIntersectedNode(); current != null
            && current != tabPane; current = current.getParent()) {
            if (current.getStyleClass().contains("tab") && getTabAt(current) != null)
                return current;
        }
        return null;
    }

    private void ensureTabOrderListener(DetachableTabPane tabPane) {
        if (tabOrderListeners.containsKey(tabPane))
            return;

        ListChangeListener<Tab> listener = _ -> {
            reconcilePaneMembership(tabPane);
            scheduleTabOrderUpdate(tabPane);
            Platform.runLater(() -> {
                registerEditorSplitAncestors(tabPane);
                trackDetachedWindow(tabPane);
            });
        };
        tabPane.getTabs().addListener(listener);
        tabOrderListeners.put(tabPane, listener);
        keepPinnedTabsOnLeft(tabPane);
    }

    private void reconcilePaneMembership(DetachableTabPane tabPane) {
        String groupId = editorGroupIds.get(tabPane);
        if (groupId == null)
            return;

        tabPane.getTabs().stream()
            .map(tabsByControl::get)
            .filter(Objects::nonNull)
            .forEach(tab -> tab.setEditorGroupId(groupId));
    }

    private void registerEditorSplitAncestors(DetachableTabPane tabPane) {
        for (Parent ancestor = tabPane.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor instanceof SplitPane splitPane && isEditorOnlySplit(splitPane)) {
                editorSplitPanes.add(splitPane);
                collectEditorPanes(splitPane).forEach(pane -> {
                    ensureSelectionListener(pane, ensureEditorGroupId(pane));
                    trackEmptySplitGroup(pane);
                    reconcilePaneMembership(pane);
                });
            }
        }
    }

    private boolean isEditorOnlySplit(SplitPane splitPane) {
        List<DetachableTabPane> panes = collectEditorPanes(splitPane);
        return !panes.isEmpty()
            && panes.stream().allMatch(pane -> pane.getScope().equals(tabPaneScope(panes.getFirst())));
    }

    private static String tabPaneScope(DetachableTabPane tabPane) {
        return Objects.toString(tabPane.getScope(), "");
    }

    private static List<DetachableTabPane> collectEditorPanes(Node root) {
        var panes = new ArrayList<DetachableTabPane>();
        collectTabPanes(root, panes);
        return panes;
    }

    private static void collectTabPanes(Node node, List<DetachableTabPane> panes) {
        if (node instanceof DetachableTabPane tabPane) {
            panes.add(tabPane);
            return;
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectTabPanes(child, panes));
        }
    }

    private void trackDetachedWindow(DetachableTabPane tabPane) {
        if (tabPane.getScene() == null || tabPane.getScene().getWindow() == null)
            return;

        Window window = tabPane.getScene().getWindow();
        if (!(window instanceof Stage) || !trackedDetachedWindows.add(window))
            return;

        window.addEventHandler(WindowEvent.WINDOW_HIDDEN, _ -> unregisterWindow(window));
    }

    private void unregisterWindow(Window window) {
        trackedDetachedWindows.remove(window);
        List.copyOf(selectionListeners.keySet()).stream()
            .filter(pane -> pane.getScene() != null && pane.getScene().getWindow() == window)
            .forEach(pane -> {
                untrackEmptySplitGroup(pane);
                removeSelectionListener(pane);
            });
    }

    private void scheduleTabOrderUpdate(DetachableTabPane tabPane) {
        if (!pendingTabOrderUpdates.add(tabPane))
            return;

        Platform.runLater(() -> {
            pendingTabOrderUpdates.remove(tabPane);
            if (tabOrderListeners.containsKey(tabPane)) {
                keepPinnedTabsOnLeft(tabPane);
            }
        });
    }

    private void keepPinnedTabsOnLeft(TabPane tabPane) {
        if (tabPane == null || tabPane.getTabs().size() < 2)
            return;

        boolean encounteredOtherTab = false;
        boolean requiresReorder = false;
        for (Tab tab : tabPane.getTabs()) {
            EditorTab editorTab = tabsByControl.get(tab);
            if (editorTab != null && editorTab.pinned()) {
                if (encounteredOtherTab) {
                    requiresReorder = true;
                    break;
                }
            } else {
                encounteredOtherTab = true;
            }
        }
        if (!requiresReorder)
            return;

        List<Tab> orderedTabs = new ArrayList<>(tabPane.getTabs().size());
        tabPane.getTabs().stream()
            .filter(tab -> {
                EditorTab editorTab = tabsByControl.get(tab);
                return editorTab != null && editorTab.pinned();
            })
            .forEach(orderedTabs::add);
        tabPane.getTabs().stream()
            .filter(tab -> {
                EditorTab editorTab = tabsByControl.get(tab);
                return editorTab == null || !editorTab.pinned();
            })
            .forEach(orderedTabs::add);
        tabPane.getTabs().setAll(orderedTabs);
    }

    private void removeSelectionListener(DetachableTabPane tabPane) {
        ChangeListener<Tab> listener = selectionListeners.remove(tabPane);
        if (listener != null) {
            tabPane.getSelectionModel().selectedItemProperty().removeListener(listener);
        }
        EventHandler<MouseEvent> mouseKeybindHandler = mouseKeybindHandlers.remove(tabPane);
        if (mouseKeybindHandler != null) {
            tabPane.removeEventFilter(MouseEvent.MOUSE_CLICKED, mouseKeybindHandler);
        }
        EditorTabStripSupport stripSupport = tabStripSupport.remove(tabPane);
        if (stripSupport != null) {
            stripSupport.close();
        }
        ListChangeListener<Tab> tabOrderListener = tabOrderListeners.remove(tabPane);
        if (tabOrderListener != null) {
            tabPane.getTabs().removeListener(tabOrderListener);
        }
        pendingTabOrderUpdates.remove(tabPane);
        editorGroupIds.remove(tabPane);
    }

    private void queueSelectionUpdate(Tab selectedTab) {
        if (restoring)
            return;

        EditorTab selectedEditorTab = tabsByControl.get(selectedTab);
        if (selectedEditorTab != null) {
            pendingSelection = selectedEditorTab;
        } else if (pendingSelection != null && !pendingSelection.tab().isSelected()) {
            pendingSelection = null;
        }

        if (selectionUpdateScheduled)
            return;

        selectionUpdateScheduled = true;
        long generation = selectionGeneration;
        Platform.runLater(() -> applyPendingSelection(generation));
    }

    private void applyPendingSelection(long generation) {
        if (generation != selectionGeneration)
            return;

        selectionUpdateScheduled = false;
        EditorTab selectedTab = pendingSelection;
        pendingSelection = null;
        if (selectedTab == null || !selectedTab.tab().isSelected()) {
            selectedTab = selectedManagedTab().orElse(null);
        }
        activate(selectedTab);
    }

    private Optional<EditorTab> selectedManagedTab() {
        return selectionListeners.keySet().stream()
            .sorted(Comparator.comparing(tabPane -> editorGroupIds.getOrDefault(tabPane, DEFAULT_EDITOR_GROUP_ID)))
            .map(tabPane -> tabPane.getSelectionModel().getSelectedItem())
            .map(tabsByControl::get)
            .filter(Objects::nonNull)
            .findFirst();
    }

    private void activate(EditorTab editorTab) {
        if (editorTab == null) {
            Services.IDE_STATE.setActiveDocument(null);
            Services.DOCUMENT_EDITOR_STATE.setActiveEditor(null, null);
            return;
        }

        markRecentlyUsed(editorTab);
        Services.IDE_STATE.setActiveDocument(editorTab.document());
        Services.DOCUMENT_EDITOR_STATE.setActiveEditor(
            editorTab.view().activeEditor(),
            editorTab.view().languageId());
        if (Boolean.TRUE.equals(Settings.SYNCHRONIZE_PROJECT_EXPLORER_WITH_ACTIVE_TAB.getValue())) {
            revealInProjectExplorer(editorTab);
        }
    }

    private void markRecentlyUsed(EditorTab editorTab) {
        if (openTabs.get(editorTab.documentId()) != editorTab)
            return;

        tabsByRecency.remove(editorTab);
        tabsByRecency.add(editorTab);
    }

    private void scheduleTabLimitEnforcement() {
        JavaFXUtils.runOnApplicationThread(
            () -> Platform.runLater(() -> enforceTabLimit(activeTab().orElse(null))));
    }

    private void enforceTabLimit(EditorTab protectedTab) {
        int tabLimit = EditorTabRetentionPolicy.normalizeLimit(Settings.EDITOR_TAB_LIMIT.getValue());
        if (tabLimit == 0 || openTabs.size() <= tabLimit)
            return;

        Set<EditorTab> excluded = Collections.newSetFromMap(new IdentityHashMap<>());
        if (protectedTab != null) {
            excluded.add(protectedTab);
        }

        boolean evictedTab = false;
        while (openTabs.size() > tabLimit) {
            EditorTab candidate = EditorTabRetentionPolicy.findLeastRecentlyUsedEvictable(
                tabsByRecency,
                excluded,
                tab -> openTabs.get(tab.documentId()) == tab && !tab.dirty() && !tab.pinned());
            if (candidate == null)
                break;

            excluded.add(candidate);
            evictedTab |= requestClose(candidate);
        }

        if (evictedTab && protectedTab != null && openTabs.get(protectedTab.documentId()) == protectedTab) {
            TabPane tabPane = protectedTab.tab().getTabPane();
            if (tabPane != null) {
                tabPane.getSelectionModel().select(protectedTab.tab());
            }
            queueSelectionUpdate(protectedTab.tab());
            activate(protectedTab);
        }
    }

    private void trimRecentlyClosedTabs(Integer limit) {
        EditorTabRetentionPolicy.trimMostRecentFirst(
            recentlyClosedTabs,
            EditorTabRetentionPolicy.normalizeLimit(limit));
    }

    /**
     * Resolves the selected managed tab, falling back to the active document.
     *
     * @return active editor tab, or an empty optional when none is available
     */
    public Optional<EditorTab> activeTab() {
        EditorTab activeTab = Optional.ofNullable(Services.IDE_STATE.getActiveDocument())
            .map(Services.IDE_STATE::identifyDocument)
            .map(DocumentIdentity::id)
            .map(openTabs::get)
            .orElse(null);
        if (activeTab != null && activeTab.tab().getTabPane() != null) {
            EditorTab selectedInActiveGroup = tabsByControl.get(
                activeTab.tab().getTabPane().getSelectionModel().getSelectedItem());
            if (selectedInActiveGroup != null)
                return Optional.of(selectedInActiveGroup);
        }
        return Optional.ofNullable(activeTab != null ? activeTab : selectedManagedTab().orElse(null));
    }

    /**
     * Selects the zero-based tab in the active editor group. Out-of-range indices are ignored.
     *
     * @param index the zero-based tab index
     */
    public void selectTab(int index) {
        if (index < 0)
            return;

        activeTab().map(EditorTab::tab)
            .map(Tab::getTabPane)
            .filter(tabPane -> index < tabPane.getTabs().size())
            .ifPresent(tabPane -> selectFromKeyboard(tabPane, tabPane.getTabs().get(index)));
    }

    /**
     * Selects the last tab in the active editor group.
     */
    public void selectLastTab() {
        activeTab().map(EditorTab::tab)
            .map(Tab::getTabPane)
            .filter(tabPane -> !tabPane.getTabs().isEmpty())
            .ifPresent(tabPane -> selectFromKeyboard(tabPane, tabPane.getTabs().getLast()));
    }

    /**
     * Selects the next tab in the active group, wrapping at the end.
     */
    public void selectNextTab() {
        selectAdjacentTab(1);
    }

    /**
     * Selects the previous tab in the active group, wrapping at the beginning.
     */
    public void selectPreviousTab() {
        selectAdjacentTab(-1);
    }

    /**
     * Moves the active tab one position to the left within its editor group.
     */
    public void moveActiveTabLeft() {
        moveActiveTab(-1);
    }

    /**
     * Moves the active tab one position to the right within its editor group.
     */
    public void moveActiveTabRight() {
        moveActiveTab(1);
    }

    private void selectAdjacentTab(int offset) {
        EditorTab editorTab = activeTab().orElse(null);
        if (editorTab == null)
            return;

        TabPane tabPane = editorTab.tab().getTabPane();
        if (tabPane == null || tabPane.getTabs().isEmpty())
            return;

        int selectedIndex = tabPane.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0) {
            selectedIndex = tabPane.getTabs().indexOf(editorTab.tab());
        }
        if (selectedIndex < 0)
            return;

        int targetIndex = Math.floorMod(selectedIndex + offset, tabPane.getTabs().size());
        selectFromKeyboard(tabPane, tabPane.getTabs().get(targetIndex));
    }

    private void selectFromKeyboard(TabPane tabPane, Tab tab) {
        EditorTab editorTab = tabsByControl.get(tab);
        if (editorTab == null)
            return;

        EditorViewState viewState = EditorViewState.capture(editorTab.view().activeEditor());
        tabPane.getSelectionModel().select(tab);
        Platform.runLater(() -> restoreEditorFocus(editorTab, viewState));
    }

    private void restoreEditorFocus(EditorTab editorTab, EditorViewState viewState) {
        if (openTabs.get(editorTab.documentId()) != editorTab || !editorTab.tab().isSelected())
            return;

        TextEditorPane editor = editorTab.view().activeEditor();
        if (editor == null) {
            editorTab.view().content().requestFocus();
            return;
        }

        int documentLength = editor.getLength();
        int anchor = Math.clamp(viewState.anchorPosition(), 0, documentLength);
        int caret = Math.clamp(viewState.caretPosition(), 0, documentLength);
        editor.selectRange(anchor, caret);
        editor.requestFocus();
    }

    private void moveActiveTab(int offset) {
        EditorTab editorTab = activeTab().orElse(null);
        if (editorTab == null)
            return;

        TabPane tabPane = editorTab.tab().getTabPane();
        if (tabPane == null)
            return;

        int currentIndex = tabPane.getTabs().indexOf(editorTab.tab());
        int targetIndex = currentIndex + offset;
        if (currentIndex < 0 || targetIndex < 0 || targetIndex >= tabPane.getTabs().size())
            return;

        EditorTab adjacentTab = tabsByControl.get(tabPane.getTabs().get(targetIndex));
        if (adjacentTab == null || adjacentTab.pinned() != editorTab.pinned())
            return;

        tabPane.getTabs().remove(currentIndex);
        tabPane.getTabs().add(targetIndex, editorTab.tab());
        tabPane.getSelectionModel().select(editorTab.tab());
    }

    /**
     * Saves the active text editor when one is available.
     *
     * @return save outcome, with no failures when there is no active editor
     */
    public SaveResult saveActive() {
        EditorTab activeTab = activeTab().orElse(null);
        if (activeTab == null || save(activeTab))
            return new SaveResult(List.of());
        return new SaveResult(List.of(activeTab));
    }

    /**
     * Attempts to save every managed tab in a non-clean state.
     *
     * @return save outcome listing the tabs that could not be saved
     */
    public SaveResult saveAll() {
        List<EditorTab> failedTabs = openTabs.values().stream()
            .filter(EditorTab::dirty)
            .filter(tab -> !save(tab))
            .toList();
        return new SaveResult(failedTabs);
    }

    /**
     * Checks whether any managed tab is in a non-clean save state.
     *
     * @return true when at least one tab is dirty
     */
    public boolean hasUnsavedChanges() {
        return openTabs.values().stream().anyMatch(EditorTab::dirty);
    }

    /**
     * Saves the active editor to a destination and rebinds its document identity and path.
     *
     * @param targetPath destination file for the active document
     * @return true if saving and any required rebinding succeed
     */
    public boolean saveAsActive(Path targetPath) {
        Objects.requireNonNull(targetPath, "Target path cannot be null");
        EditorTab editorTab = activeTab().orElse(null);
        if (editorTab == null)
            return false;

        TextEditorPane editor = editorTab.view().activeEditor();
        if (editor == null)
            return false;

        Path normalizedTarget = targetPath.toAbsolutePath().normalize();
        if (pathsMatch(editorTab.path(), normalizedTarget))
            return save(editorTab);

        Optional<DocumentIdentity> targetIdentity = Services.IDE_STATE.findDocumentIdentity(
            DocumentUri.fromPath(normalizedTarget));
        EditorTab conflictingTab = targetIdentity.map(DocumentIdentity::id).map(openTabs::get).orElse(null);
        if (conflictingTab != null && conflictingTab != editorTab) {
            Railroad.LOGGER.warn("Cannot save {} as {} because that document is already open", editorTab.path(),
                normalizedTarget);
            return false;
        }

        Path previousPath = editorTab.path();
        DocumentId previousId = editorTab.documentId();
        if (!editor.saveAs(normalizedTarget))
            return false;

        try {
            DocumentIdentity reboundIdentity = targetIdentity
                .filter(identity -> !Objects.equals(identity.id(), previousId))
                .orElseGet(() -> Services.IDE_STATE.rebindDocument(
                    editorTab.identity(),
                    DocumentUri.fromPath(normalizedTarget)));
            if (editorTab.document() instanceof FileSystemDocument fileSystemDocument) {
                fileSystemDocument.rebind(normalizedTarget);
            }
            replaceOpenTabIdentity(previousId, reboundIdentity.id());
            editorTab.rebind(reboundIdentity, normalizedTarget);
            refreshTabPresentations();
            return true;
        } catch (RuntimeException exception) {
            editor.rebind(previousPath);
            Railroad.LOGGER.error("Failed to rebind editor from {} to {}", previousPath, normalizedTarget, exception);
            return false;
        }
    }

    /**
     * Marks dirty text editors to discard their changes when closed.
     */
    public void discardUnsavedChangesOnClose() {
        openTabs.values().stream()
            .filter(EditorTab::dirty)
            .map(EditorTab::view)
            .map(EditorOpenView::activeEditor)
            .filter(Objects::nonNull)
            .forEach(TextEditorPane::discardChangesOnClose);
    }

    private boolean save(EditorTab editorTab) {
        TextEditorPane editor = editorTab.view().activeEditor();
        return editor == null || editor.saveNow();
    }

    private void replaceOpenTabIdentity(DocumentId previousId, DocumentId newId) {
        if (previousId.equals(newId))
            return;

        var reboundTabs = new LinkedHashMap<DocumentId, EditorTab>();
        openTabs.forEach((documentId, tab) -> reboundTabs.put(
            documentId.equals(previousId) ? newId : documentId,
            tab));
        openTabs.clear();
        openTabs.putAll(reboundTabs);
    }

    /**
     * Captures the main layout, detached windows, and open editor tabs.
     *
     * @return complete editor workspace session snapshot
     */
    public EditorWorkspaceSessionState captureWorkspaceSession() {
        List<EditorTabSessionState> tabs = captureSessionState();
        IDEPane idePane = Services.UI_MANAGER.lookup(UIIds.IDE.IDE).orElse(null);
        if (idePane == null)
            return EditorWorkspaceSessionState.legacy(tabs);

        Node mainRoot = idePane.getEditorLayoutRoot(WorkspaceModes.CODE);
        EditorLayoutNodeState mainLayout = captureLayoutNode(mainRoot);
        if (mainLayout == null) {
            mainLayout = EditorLayoutNodeState.group(DEFAULT_EDITOR_GROUP_ID, null);
        }

        Window mainWindow = mainRoot == null || mainRoot.getScene() == null
            ? null
            : mainRoot.getScene().getWindow();
        var detachedWindows = new ArrayList<DetachedEditorWindowState>();
        Set<Window> capturedWindows = Collections.newSetFromMap(new IdentityHashMap<>());
        selectionListeners.keySet().stream()
            .map(DetachableTabPane::getScene)
            .filter(Objects::nonNull)
            .map(Scene::getWindow)
            .filter(Objects::nonNull)
            .filter(Window::isShowing)
            .filter(window -> window != mainWindow)
            .filter(capturedWindows::add)
            .forEach(window -> {
                EditorLayoutNodeState layout = captureLayoutNode(window.getScene().getRoot());
                if (layout != null) {
                    detachedWindows.add(new DetachedEditorWindowState(
                        layout,
                        window.getX(),
                        window.getY(),
                        window.getWidth(),
                        window.getHeight(),
                        window instanceof Stage stage && stage.isMaximized()));
                }
            });

        return new EditorWorkspaceSessionState(
            EditorWorkspaceSessionState.CURRENT_SCHEMA_VERSION,
            mainLayout,
            detachedWindows,
            tabs);
    }

    private EditorLayoutNodeState captureLayoutNode(Node node) {
        if (node instanceof DetachableTabPane tabPane) {
            if (!editorGroupIds.containsKey(tabPane)
                && tabPane.getTabs().stream().noneMatch(tabsByControl::containsKey))
                return null;

            String groupId = ensureEditorGroupId(tabPane);
            reconcilePaneMembership(tabPane);
            EditorTab selectedTab = tabsByControl.get(tabPane.getSelectionModel().getSelectedItem());
            return EditorLayoutNodeState.group(
                groupId,
                selectedTab == null ? null : selectedTab.documentId().toString());
        }

        if (node instanceof SplitPane splitPane) {
            List<EditorLayoutNodeState> children = splitPane.getItems().stream()
                .map(this::captureLayoutNode)
                .filter(Objects::nonNull)
                .toList();
            if (children.isEmpty())
                return null;
            if (children.size() == 1)
                return children.getFirst();

            double[] positions = splitPane.getDividerPositions();
            List<Double> dividers = Arrays.stream(positions).boxed().toList();
            return EditorLayoutNodeState.split(splitPane.getOrientation(), dividers, children);
        }

        if (node instanceof Parent parent) {
            List<EditorLayoutNodeState> children = parent.getChildrenUnmodifiable().stream()
                .map(this::captureLayoutNode)
                .filter(Objects::nonNull)
                .toList();
            return children.size() == 1 ? children.getFirst() : null;
        }
        return null;
    }

    /**
     * Captures open tabs after applying pending selection and reconciling group membership.
     *
     * @return tab states with document identity, ordering, selection, and view state
     */
    public List<EditorTabSessionState> captureSessionState() {
        if (selectionUpdateScheduled) {
            applyPendingSelection(selectionGeneration);
        }
        selectionListeners.keySet().forEach(this::reconcilePaneMembership);

        var sessionState = new ArrayList<EditorTabSessionState>();
        Set<DocumentId> capturedTabs = new HashSet<>();
        DocumentId activeDocumentId = Optional.ofNullable(Services.IDE_STATE.getActiveDocument())
            .map(Services.IDE_STATE::identifyDocument)
            .map(DocumentIdentity::id)
            .orElse(null);

        List<DetachableTabPane> orderedGroups = selectionListeners.keySet().stream()
            .sorted(Comparator.comparing(tabPane -> editorGroupIds.getOrDefault(tabPane, DEFAULT_EDITOR_GROUP_ID)))
            .toList();
        int order = 0;
        for (DetachableTabPane tabPane : orderedGroups) {
            for (Tab tabControl : tabPane.getTabs()) {
                EditorTab editorTab = tabsByControl.get(tabControl);
                if (editorTab == null || !capturedTabs.add(editorTab.documentId()))
                    continue;

                sessionState.add(toSessionState(editorTab, order++, activeDocumentId));
            }
        }

        for (EditorTab editorTab : openTabs.values()) {
            if (capturedTabs.add(editorTab.documentId())) {
                sessionState.add(toSessionState(editorTab, order++, activeDocumentId));
            }
        }
        return List.copyOf(sessionState);
    }

    private static EditorTabSessionState toSessionState(EditorTab editorTab, int order, DocumentId activeDocumentId) {
        return new EditorTabSessionState(
            editorTab.identity(),
            editorTab.path(),
            order,
            editorTab.pinned(),
            editorTab.preview(),
            editorTab.documentId().equals(activeDocumentId),
            editorTab.editorGroupId(),
            EditorViewState.capture(editorTab.view().activeEditor()));
    }

    private void handleClosed(EditorTab editorTab) {
        if (openTabs.remove(editorTab.documentId()) == null)
            return;

        tabsByRecency.remove(editorTab);
        discardApprovedTabs.remove(editorTab);
        Stage failedCloseDialog = failedCloseDialogs.remove(editorTab);
        if (failedCloseDialog != null) {
            failedCloseDialog.close();
        }

        ClosedEditorTab closedTab = pendingCloseSnapshots.remove(editorTab.documentId());
        if (!editorTab.preview()) {
            if (closedTab == null) {
                closedTab = captureClosedTab(editorTab);
            }
            recentlyClosedTabs.removeIf(tab -> tab.documentId().equals(editorTab.documentId()));
            recentlyClosedTabs.addFirst(closedTab);
            trimRecentlyClosedTabs(Settings.RECENTLY_CLOSED_TAB_LIMIT.getValue());
        }
        tabsByControl.remove(editorTab.tab());
        refreshTabPresentations();
        Services.IDE_STATE.closeDocument(editorTab.document());
        if (Services.IDE_STATE.getActiveDocument() == null) {
            Services.DOCUMENT_EDITOR_STATE.setActiveEditor(null, null);
        }
        if (openTabs.isEmpty()) {
            restoreEmptyEditorState();
        }
    }

    private void restoreEmptyEditorState() {
        IDEContentRouter.routeActive(WorkspaceContentTargets.CODE_EDITOR, tabPane -> {
            if (!openTabs.isEmpty() || tabPane.getTabs().stream()
                .anyMatch(tab -> tab.getContent() instanceof IDEWelcomePane))
                return;

            var welcomeTab = new Tab("Welcome", new IDEWelcomePane());
            welcomeTab.setId("editor:welcome");
            welcomeTab.setClosable(false);
            tabPane.getTabs().add(welcomeTab);
            tabPane.getSelectionModel().select(welcomeTab);
        });
    }

    private void handleCloseRequest(EditorTab editorTab, Event event) {
        if (discardApprovedTabs.remove(editorTab)) {
            pendingCloseSnapshots.put(editorTab.documentId(), captureClosedTab(editorTab));
            return;
        }

        if (editorTab.dirty() && !save(editorTab)) {
            event.consume();
            TextEditorPane editor = editorTab.view().activeEditor();
            if (editor == null || !editor.hasPendingExternalChange()) {
                showFailedCloseDialog(editorTab);
            }
            return;
        }

        pendingCloseSnapshots.put(editorTab.documentId(), captureClosedTab(editorTab));
    }

    private void showFailedCloseDialog(EditorTab editorTab) {
        Stage existingDialog = failedCloseDialogs.get(editorTab);
        if (existingDialog != null) {
            existingDialog.toFront();
            existingDialog.requestFocus();
            return;
        }

        var content = new LocalizedLabel("railroad.ide.close_tab_failed.content", editorTab.path());
        content.setWrapText(true);
        content.setMaxWidth(560);

        var saveButton = new RRButton("railroad.generic.save");
        saveButton.setVariant(ButtonVariant.PRIMARY);
        saveButton.setDefaultButton(true);
        var discardButton = new RRButton("railroad.generic.discard");
        discardButton.setVariant(ButtonVariant.DANGER);
        var cancelButton = new RRButton("railroad.generic.cancel");
        cancelButton.setVariant(ButtonVariant.SECONDARY);

        Stage dialog = WindowBuilder.createDialog(
            "railroad.ide.close_tab_failed.window_title",
            new DialogBuilder()
                .title("railroad.ide.close_tab_failed.title")
                .contentNode(content)
                .buttons(saveButton, discardButton, cancelButton));
        failedCloseDialogs.put(editorTab, dialog);
        dialog.addEventHandler(WindowEvent.WINDOW_HIDDEN, _ -> failedCloseDialogs.remove(editorTab, dialog));

        saveButton.setOnAction(_ -> {
            if (openTabs.get(editorTab.documentId()) != editorTab) {
                dialog.close();
            } else if (save(editorTab)) {
                dialog.close();
                requestClose(editorTab);
            }
        });
        discardButton.setOnAction(_ -> {
            var editor = editorTab.view().activeEditor();
            if (editor != null) {
                editor.discardChangesOnClose();
            }
            discardApprovedTabs.add(editorTab);
            dialog.close();
            requestClose(editorTab);
        });
        cancelButton.setOnAction(_ -> dialog.close());
    }

    private Optional<EditorTab> findOpen(Path path) {
        if (path == null)
            return Optional.empty();

        return Services.IDE_STATE.findDocumentIdentity(DocumentUri.fromPath(path))
            .map(DocumentIdentity::id)
            .map(openTabs::get);
    }

    private void handleRenamed(DocumentRenamedEvent event) {
        Optional<Path> newPath = event.file().getUri().filePath();
        if (newPath.isEmpty())
            return;

        Path normalizedNewPath = newPath.get().toAbsolutePath().normalize();
        Path previousPath = normalizedNewPath.resolveSibling(event.oldName());
        Runnable update = () -> findOpen(previousPath).ifPresent(editorTab -> {
            DocumentIdentity reboundIdentity = Services.IDE_STATE.rebindDocument(
                editorTab.identity(),
                DocumentUri.fromPath(normalizedNewPath));
            if (editorTab.document() instanceof FileSystemDocument fileSystemDocument) {
                fileSystemDocument.rebind(normalizedNewPath);
            }

            if (editorTab.view().activeEditor() != null) {
                editorTab.view().activeEditor().rebind(normalizedNewPath);
            }

            editorTab.rebind(reboundIdentity, normalizedNewPath);
            refreshTabPresentations();
        });
        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            JavaFXUtils.runOnApplicationThread(update);
        }
    }

    private void refreshTabPresentations() {
        List<EditorTab> tabs = List.copyOf(openTabs.values());
        List<String> titles = EditorTabPresentation.disambiguatedTitles(
            tabs.stream().map(EditorTab::path).toList());
        for (int index = 0; index < tabs.size(); index++) {
            tabs.get(index).setDisplayTitle(titles.get(index));
        }
    }

    private void handleDocumentEvent(DocumentEvent event) {
        if (!event.isDeletedEvent())
            return;

        Optional<Path> deletedPath = event.file().getUri().filePath();
        if (deletedPath.isEmpty())
            return;

        Runnable update = () -> findOpen(deletedPath.get()).ifPresent(editorTab -> {
            if (editorTab.view().activeEditor() != null) {
                editorTab.view().activeEditor().markBackingFileDeleted();
            }
        });
        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            JavaFXUtils.runOnApplicationThread(update);
        }
    }

    private void handleProjectClosed(ProjectEvent event) {
        if (event.isClosed()) {
            if (selectionListeners.isEmpty()) {
                resetTracking();
            } else {
                JavaFXUtils.runOnApplicationThread(this::resetTracking);
            }
        }
    }

    private static boolean pathsMatch(Path first, Path second) {
        if (first == null || second == null)
            return false;

        Path normalizedFirst = first.toAbsolutePath().normalize();
        Path normalizedSecond = second.toAbsolutePath().normalize();
        if (normalizedFirst.equals(normalizedSecond))
            return true;
        if (File.separatorChar == '\\'
            && normalizedFirst.toString().equalsIgnoreCase(normalizedSecond.toString()))
            return true;

        try {
            return Files.exists(normalizedFirst)
                && Files.exists(normalizedSecond)
                && Files.isSameFile(normalizedFirst, normalizedSecond);
        } catch (IOException _) {
            return false;
        }
    }

    private void resetTracking() {
        selectionGeneration++;
        selectionUpdateScheduled = false;
        replacingPreview = false;
        pendingSelection = null;
        primaryEditorPane = null;
        selectionListeners.forEach(
            (tabPane, listener) -> tabPane.getSelectionModel().selectedItemProperty().removeListener(listener));
        selectionListeners.clear();
        editorGroupIds.clear();
        emptyGroupListeners.forEach((tabPane, listener) -> tabPane.getTabs().removeListener(listener));
        emptyGroupListeners.clear();
        tabOrderListeners.forEach((tabPane, listener) -> tabPane.getTabs().removeListener(listener));
        tabOrderListeners.clear();
        mouseKeybindHandlers.forEach(
            (tabPane, handler) -> tabPane.removeEventFilter(MouseEvent.MOUSE_CLICKED, handler));
        mouseKeybindHandlers.clear();
        tabStripSupport.values().forEach(EditorTabStripSupport::close);
        tabStripSupport.clear();
        pendingTabOrderUpdates.clear();
        editorSplitPanes.clear();
        trackedDetachedWindows.clear();
        tabsByControl.clear();
        tabsByRecency.clear();
        pendingCloseSnapshots.clear();
        List.copyOf(failedCloseDialogs.values()).forEach(Stage::close);
        failedCloseDialogs.clear();
        discardApprovedTabs.clear();
        openTabs.clear();
        recentlyClosedTabs.clear();
        Services.DOCUMENT_EDITOR_STATE.setActiveEditor(null, null);
    }

    /**
     * Requests closure of a managed tab through its close lifecycle.
     *
     * @param tab editor tab to act on
     */
    public void close(EditorTab tab) {
        requireManaged(tab);
        requestClose(tab);
    }

    /**
     * Resolves a managed tab from a tab-pane selection or a node identifier.
     *
     * @param target node identifying a tab or tab pane
     * @return matching editor tab, or null when the target does not identify one
     */
    public EditorTab getTabAt(Node target) {
        if (target == null)
            return null;

        if (target instanceof TabPane tabPane)
            return tabsByControl.get(tabPane.getSelectionModel().getSelectedItem());

        String targetId = target.getId();
        if (targetId == null)
            return null;

        EditorTab editorTab = tabsByControl.entrySet().stream()
            .filter(entry -> targetId.equals(entry.getKey().getId()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
        if (editorTab != null)
            return editorTab;

        Parent headerContainer = target.getParent();
        TabPane tabPane = containingTabPane(headerContainer);
        if (headerContainer == null || tabPane == null)
            return null;

        int headerIndex = headerContainer.getChildrenUnmodifiable().indexOf(target);
        return headerIndex >= 0 && headerIndex < tabPane.getTabs().size()
            ? tabsByControl.get(tabPane.getTabs().get(headerIndex))
            : null;
    }

    private static TabPane containingTabPane(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof TabPane tabPane)
                return tabPane;
        }

        return null;
    }

    /**
     * Requests closure of other unpinned tabs in the same editor group.
     *
     * @param tab editor tab to act on
     */
    public void closeOthers(EditorTab tab) {
        requireManaged(tab);
        closeInDescendingOrder(tabsInSamePane(tab).stream()
            .filter(candidate -> candidate != tab && !candidate.pinned())
            .toList());
    }

    /**
     * Requests closure of unpinned tabs to the right in the same editor group.
     *
     * @param tab editor tab to act on
     */
    public void closeToRight(EditorTab tab) {
        requireManaged(tab);
        int tabIndex = tabIndex(tab);
        closeInDescendingOrder(tabsInSamePane(tab).stream()
            .filter(candidate -> tabIndex(candidate) > tabIndex && !candidate.pinned())
            .toList());
    }

    /**
     * Requests closure of unpinned tabs to the left in the same editor group.
     *
     * @param tab editor tab to act on
     */
    public void closeToLeft(EditorTab tab) {
        requireManaged(tab);
        int tabIndex = tabIndex(tab);
        closeInDescendingOrder(tabsInSamePane(tab).stream()
            .filter(candidate -> tabIndex(candidate) < tabIndex && !candidate.pinned())
            .toList());
    }

    /**
     * Requests closure of all managed editor tabs.
     */
    public void closeAll() {
        closeInDescendingOrder(new ArrayList<>(openTabs.values()));
    }

    /**
     * Requests closure of all unpinned editor tabs.
     */
    public void closeAllUnpinned() {
        closeInDescendingOrder(openTabs.values().stream()
            .filter(tab -> !tab.pinned())
            .toList());
    }

    /**
     * Requests closure of tabs whose editor state is clean.
     */
    public void closeAllUnmodified() {
        closeInDescendingOrder(openTabs.values().stream()
            .filter(tab -> !tab.dirty())
            .toList());
    }

    /**
     * Requests closure of tabs whose editor state is clean.
     */
    public void closeAllSaved() {
        closeInDescendingOrder(openTabs.values().stream()
            .filter(tab -> !tab.dirty())
            .toList());
    }

    /**
     * Promotes a managed preview to a permanent tab and pins it.
     *
     * @param tab editor tab to act on
     */
    public void pin(EditorTab tab) {
        Objects.requireNonNull(tab, "Tab cannot be null");
        if (openTabs.containsKey(tab.documentId())) {
            promote(tab);
            tab.setPinned(true);
        }
    }

    /**
     * Clears the pinned flag of a managed editor tab.
     *
     * @param tab editor tab to act on
     */
    public void unpin(EditorTab tab) {
        Objects.requireNonNull(tab, "Tab cannot be null");
        if (openTabs.containsKey(tab.documentId())) {
            tab.setPinned(false);
        }
    }

    /**
     * Toggles a managed tab's pin status, promoting previews before pinning.
     *
     * @param tab editor tab to act on
     */
    public void togglePin(EditorTab tab) {
        Objects.requireNonNull(tab, "Tab cannot be null");
        if (openTabs.containsKey(tab.documentId())) {
            if (!tab.pinned()) {
                promote(tab);
            }
            tab.setPinned(!tab.pinned());
        }
    }

    /**
     * Updates preview status, refusing pinned or dirty previews and promoting any previous preview.
     *
     * @param tab editor tab to act on
     * @param preview whether the tab occupies the reusable preview slot
     */
    public void setPreview(EditorTab tab, boolean preview) {
        Objects.requireNonNull(tab, "Tab cannot be null");
        if (openTabs.containsKey(tab.documentId())) {
            if (preview && (tab.pinned() || tab.dirty()))
                return;

            if (preview) {
                previewTab()
                    .filter(existing -> existing != tab)
                    .ifPresent(this::promote);
            }
            tab.setPreview(preview);
        }
    }

    /**
     * Returns the single reusable preview tab, if one is currently open.
     *
     * @return preview tab, or an empty optional when none exists
     */
    public Optional<EditorTab> previewTab() {
        return openTabs.values().stream().filter(EditorTab::preview).findFirst();
    }

    /**
     * Makes a temporary preview a normal document tab.
     *
     * @param tab editor tab to act on
     */
    public void promote(EditorTab tab) {
        Objects.requireNonNull(tab, "Tab cannot be null");
        if (openTabs.get(tab.documentId()) == tab && tab.preview()) {
            tab.setPreview(false);
        }
    }

    /**
     * Reopens and activates the most recently closed retained tab.
     */
    public void reopenLastClosed() {
        ClosedEditorTab lastClosed = recentlyClosedTabs.peekFirst();
        if (lastClosed != null) {
            reopen(lastClosed, true);
        }
    }

    /**
     * Checks whether an editor tab can be reopened.
     *
     * @return whether the closed-tab history is nonempty
     */
    public boolean hasRecentlyClosedTabs() {
        return !recentlyClosedTabs.isEmpty();
    }

    /**
     * Reopens and activates a tab if it remains in the closed-tab history.
     *
     * @param tab editor tab to act on
     */
    public void reopenClosed(ClosedEditorTab tab) {
        Objects.requireNonNull(tab, "Closed tab cannot be null");
        if (recentlyClosedTabs.contains(tab)) {
            reopen(tab, true);
        }
    }

    /**
     * Reopens retained closed tabs in their previous placement order.
     */
    public void reopenAllClosed() {
        if (recentlyClosedTabs.isEmpty())
            return;

        Project project = Services.IDE_STATE.getCurrentProject();
        if (project == null)
            return;

        List<ClosedEditorTab> mostRecentFirst = List.copyOf(recentlyClosedTabs);
        List<ClosedEditorTab> placementOrder = mostRecentFirst.stream()
            .sorted(Comparator.comparing(ClosedEditorTab::editorGroupId)
                .thenComparingInt(ClosedEditorTab::previousIndex))
            .toList();
        IDEContentRouter.routeActive(WorkspaceContentTargets.CODE_EDITOR, tabPane -> {
            Map<ClosedEditorTab, EditorTab> reopenedTabs = new LinkedHashMap<>();
            for (ClosedEditorTab closedTab : placementOrder) {
                EditorTab reopenedTab = reopenInTabPane(project, tabPane, closedTab, false);
                if (reopenedTab != null) {
                    reopenedTabs.put(closedTab, reopenedTab);
                }
            }

            EditorTab activeTab = mostRecentFirst.stream()
                .map(reopenedTabs::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
            if (activeTab != null) {
                tabPane.getSelectionModel().select(activeTab.tab());
                activate(activeTab);
            }
        });
    }

    /**
     * Reveals an existing managed document in the operating system file explorer.
     *
     * @param tab editor tab to act on
     */
    public void revealInFileExplorer(EditorTab tab) {
        Objects.requireNonNull(tab, "Tab cannot be null");
        if (!openTabs.containsKey(tab.documentId()))
            return;

        Path path = tab.path();
        if (path == null || !Files.exists(path))
            return;

        FileUtils.openInExplorer(path);
    }

    /**
     * Reveals an existing managed document in the project explorer.
     *
     * @param tab editor tab to act on
     */
    public void revealInProjectExplorer(EditorTab tab) {
        Objects.requireNonNull(tab, "Tab cannot be null");
        if (!openTabs.containsKey(tab.documentId()))
            return;

        Path path = tab.path();
        if (path == null || !Files.exists(path))
            return;

        Services.UI_MANAGER.lookup(UIIds.IDE.PROJECT_EXPLORER).ifPresent(
            explorer -> explorer.revealPath(path));
    }

    /**
     * Opens a terminal at the parent directory of an existing document file.
     *
     * @param tab editor tab to act on
     */
    public void openInTerminal(EditorTab tab) {
        Objects.requireNonNull(tab, "Tab cannot be null");
        if (!openTabs.containsKey(tab.documentId()))
            return;

        Path path = tab.path();
        if (path == null || !Files.exists(path))
            return;

        FileUtils.openInTerminal(path.getParent());
    }

    /**
     * Moves a managed tab to the previous editor group when available.
     *
     * @param tab editor tab to act on
     */
    public void moveToPreviousGroup(EditorTab tab) {
        moveToAdjacentGroup(tab, -1);
    }

    /**
     * Moves a managed tab to the next editor group when available.
     *
     * @param tab editor tab to act on
     */
    public void moveToNextGroup(EditorTab tab) {
        moveToAdjacentGroup(tab, 1);
    }

    private void moveToAdjacentGroup(EditorTab tab, int offset) {
        Objects.requireNonNull(tab, "Tab cannot be null");
        if (openTabs.get(tab.documentId()) != tab)
            return;

        JavaFXUtils.runOnApplicationThread(() -> moveToAdjacentGroupOnApplicationThread(tab, offset));
    }

    private void moveToAdjacentGroupOnApplicationThread(EditorTab editorTab, int offset) {
        if (openTabs.get(editorTab.documentId()) != editorTab)
            return;

        if (!(editorTab.tab().getTabPane() instanceof DetachableTabPane sourceTabPane))
            return;

        ensureSelectionListener(sourceTabPane, editorTab.editorGroupId());
        List<DetachableTabPane> groups = orderedEditorGroups(sourceTabPane);
        int sourceIndex = groups.indexOf(sourceTabPane);
        int targetIndex = sourceIndex + offset;
        if (sourceIndex < 0 || targetIndex < 0 || targetIndex >= groups.size())
            return;

        DetachableTabPane targetTabPane = groups.get(targetIndex);
        String previousGroupId = editorTab.editorGroupId();
        String targetGroupId = ensureEditorGroupId(targetTabPane);
        Tab tab = editorTab.tab();
        int previousIndex = sourceTabPane.getTabs().indexOf(tab);
        if (previousIndex < 0)
            return;

        try {
            sourceTabPane.getTabs().remove(previousIndex);
            addToTabPane(targetTabPane, tab, -1);
            targetTabPane.getSelectionModel().select(tab);
            editorTab.setEditorGroupId(targetGroupId);
            queueSelectionUpdate(tab);
            activate(editorTab);
        } catch (RuntimeException exception) {
            if (tab.getTabPane() == targetTabPane) {
                targetTabPane.getTabs().remove(tab);
            }
            if (tab.getTabPane() == null) {
                sourceTabPane.getTabs().add(Math.min(previousIndex, sourceTabPane.getTabs().size()), tab);
                sourceTabPane.getSelectionModel().select(tab);
            }
            editorTab.setEditorGroupId(previousGroupId);
            Railroad.LOGGER.error("Failed to move editor tab {} to an adjacent group", editorTab.path(), exception);
        }
    }

    private String ensureEditorGroupId(DetachableTabPane tabPane) {
        String groupId = editorGroupIds.get(tabPane);
        if (groupId == null) {
            groupId = tabPane.getTabs().stream()
                .map(tabsByControl::get)
                .filter(Objects::nonNull)
                .map(EditorTab::editorGroupId)
                .findFirst()
                .orElseGet(this::nextEditorGroupId);
            ensureSelectionListener(tabPane, groupId);
        }
        return groupId;
    }

    private List<DetachableTabPane> orderedEditorGroups(DetachableTabPane sourceTabPane) {
        if (sourceTabPane.getScene() == null)
            return List.of();

        var groups = new ArrayList<DetachableTabPane>();
        collectEditorGroups(sourceTabPane.getScene().getRoot(), groups);
        return groups;
    }

    private void collectEditorGroups(Node node, List<DetachableTabPane> groups) {
        if (node instanceof DetachableTabPane tabPane) {
            if (selectionListeners.containsKey(tabPane)
                || tabPane.getTabs().stream().anyMatch(tabsByControl::containsKey)) {
                groups.add(tabPane);
            }

            return;
        }

        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectEditorGroups(child, groups));
        }
    }

    /**
     * Moves the tab into a new editor group to the right of its current group.
     *
     * @param tab editor tab to act on
     */
    public void splitRight(EditorTab tab) {
        split(tab, Orientation.HORIZONTAL);
    }

    /**
     * Moves the tab into a new editor group below its current group.
     *
     * @param tab editor tab to act on
     */
    public void splitDown(EditorTab tab) {
        split(tab, Orientation.VERTICAL);
    }

    /**
     * Checks for unpinned tabs to the left in the same editor group.
     *
     * @param tab editor tab to act on
     * @return true when an unpinned tab precedes this tab
     */
    public boolean hasTabsToLeft(EditorTab tab) {
        return hasTabsBeside(tab, index -> index < tabIndex(tab));
    }

    /**
     * Checks for unpinned tabs to the right in the same editor group.
     *
     * @param tab editor tab to act on
     * @return true when an unpinned tab follows this tab
     */
    public boolean hasTabsToRight(EditorTab tab) {
        return hasTabsBeside(tab, index -> index > tabIndex(tab));
    }

    /**
     * Checks for other unpinned tabs in the same editor group.
     *
     * @param tab editor tab to act on
     * @return true when another unpinned tab exists
     */
    public boolean hasOtherClosableTabs(EditorTab tab) {
        Objects.requireNonNull(tab, "Tab cannot be null");
        if (openTabs.get(tab.documentId()) != tab)
            return false;

        return tabsInSamePane(tab).stream()
            .anyMatch(candidate -> candidate != tab && !candidate.pinned());
    }

    /**
     * Checks whether the tab has a preceding editor group.
     *
     * @param tab editor tab to act on
     * @return true when the tab can move to the previous group
     */
    public boolean hasPreviousEditorGroup(EditorTab tab) {
        return hasAdjacentEditorGroup(tab, -1);
    }

    /**
     * Checks whether the tab has a following editor group.
     *
     * @param tab editor tab to act on
     * @return true when the tab can move to the next group
     */
    public boolean hasNextEditorGroup(EditorTab tab) {
        return hasAdjacentEditorGroup(tab, 1);
    }

    private boolean hasTabsBeside(EditorTab tab, IntPredicate positionPredicate) {
        Objects.requireNonNull(tab, "Tab cannot be null");
        if (openTabs.get(tab.documentId()) != tab || tabIndex(tab) < 0)
            return false;

        return tabsInSamePane(tab).stream()
            .filter(candidate -> !candidate.pinned())
            .mapToInt(this::tabIndex)
            .anyMatch(positionPredicate);
    }

    private boolean hasAdjacentEditorGroup(EditorTab tab, int offset) {
        Objects.requireNonNull(tab, "Tab cannot be null");
        if (openTabs.get(tab.documentId()) != tab
            || !(tab.tab().getTabPane() instanceof DetachableTabPane sourceTabPane))
            return false;

        List<DetachableTabPane> groups = orderedEditorGroups(sourceTabPane);
        int sourceIndex = groups.indexOf(sourceTabPane);
        int targetIndex = sourceIndex + offset;
        return sourceIndex >= 0 && targetIndex >= 0 && targetIndex < groups.size();
    }

    private void split(EditorTab tab, Orientation orientation) {
        Objects.requireNonNull(tab, "Tab cannot be null");
        if (openTabs.get(tab.documentId()) != tab)
            return;

        JavaFXUtils.runOnApplicationThread(() -> splitOnApplicationThread(tab, orientation));
    }

    private void splitOnApplicationThread(EditorTab editorTab, Orientation orientation) {
        if (openTabs.get(editorTab.documentId()) != editorTab)
            return;

        if (!(editorTab.tab().getTabPane() instanceof DetachableTabPane sourceTabPane))
            return;

        DetachableTabPane targetTabPane = createSiblingTabPane(sourceTabPane);
        String targetGroupId = nextEditorGroupId();
        ensureSelectionListener(targetTabPane, targetGroupId);
        trackEmptySplitGroup(sourceTabPane);
        trackEmptySplitGroup(targetTabPane);

        if (!insertAdjacent(sourceTabPane, targetTabPane, orientation)) {
            SplitPane sourceParent = findContainingSplitPane(sourceTabPane);
            if (!editorSplitPanes.contains(sourceParent)) {
                untrackEmptySplitGroup(sourceTabPane);
            }
            untrackEmptySplitGroup(targetTabPane);
            removeSelectionListener(targetTabPane);
            return;
        }

        Tab tab = editorTab.tab();
        int previousIndex = sourceTabPane.getTabs().indexOf(tab);
        try {
            sourceTabPane.getTabs().remove(tab);
            targetTabPane.getTabs().add(tab);
            targetTabPane.getSelectionModel().select(tab);
            editorTab.setEditorGroupId(targetGroupId);
            queueSelectionUpdate(tab);
            activate(editorTab);
        } catch (RuntimeException exception) {
            targetTabPane.getTabs().remove(tab);
            if (tab.getTabPane() == null) {
                sourceTabPane.getTabs().add(Math.clamp(previousIndex, 0, sourceTabPane.getTabs().size()), tab);
                sourceTabPane.getSelectionModel().select(tab);
            }
            removeEmptySplitPane(targetTabPane);
            Railroad.LOGGER.error("Failed to split editor tab {}", editorTab.path(), exception);
        }
    }

    private DetachableTabPane createSiblingTabPane(DetachableTabPane sourceTabPane) {
        var sibling = new DetachableTabPane();
        sibling.setSceneFactory(sourceTabPane.getSceneFactory());
        sibling.setStageOwnerFactory(sourceTabPane.getStageOwnerFactory());
        sibling.setScope(sourceTabPane.getScope());
        sibling.setTabClosingPolicy(sourceTabPane.getTabClosingPolicy());
        sibling.setCloseIfEmpty(false);
        sibling.setDetachableTabPaneFactory(sourceTabPane.getDetachableTabPaneFactory());
        sibling.setStageFactory(sourceTabPane.getStageFactory());
        sibling.setDropHint(sourceTabPane.getDropHint());
        Services.UI_MANAGER.lookup(UIIds.IDE.IDE).ifPresent(idePane -> idePane.trackEditorPane(sibling));
        return sibling;
    }

    private boolean insertAdjacent(
        DetachableTabPane sourceTabPane,
        DetachableTabPane targetTabPane,
        Orientation orientation
    ) {
        SplitPane containingSplitPane = findContainingSplitPane(sourceTabPane);
        if (editorSplitPanes.contains(containingSplitPane)
            && containingSplitPane.getOrientation() == orientation) {
            int sourceIndex = containingSplitPane.getItems().indexOf(sourceTabPane);
            if (sourceIndex < 0)
                return false;

            containingSplitPane.getItems().add(sourceIndex + 1, targetTabPane);
            distributeEvenly(containingSplitPane);
            return true;
        }

        var splitPane = new SplitPane();
        splitPane.setOrientation(orientation);
        if (containingSplitPane != null) {
            int sourceIndex = containingSplitPane.getItems().indexOf(sourceTabPane);
            if (sourceIndex < 0)
                return false;
            containingSplitPane.getItems().set(sourceIndex, splitPane);
        } else if (sourceTabPane.getParent() instanceof Pane parentPane) {
            int sourceIndex = parentPane.getChildren().indexOf(sourceTabPane);
            if (sourceIndex < 0)
                return false;
            parentPane.getChildren().set(sourceIndex, splitPane);
        } else if (sourceTabPane.getParent() == null
            && sourceTabPane.getScene() != null
            && sourceTabPane.getScene().getRoot() == sourceTabPane) {
            sourceTabPane.getScene().setRoot(splitPane);
        } else
            return false;

        splitPane.getItems().addAll(sourceTabPane, targetTabPane);
        splitPane.setDividerPositions(0.5);
        editorSplitPanes.add(splitPane);
        return true;
    }

    private static SplitPane findContainingSplitPane(Node node) {
        Parent ancestor = node.getParent();
        while (ancestor != null) {
            if (ancestor instanceof SplitPane splitPane && splitPane.getItems().contains(node))
                return splitPane;
            ancestor = ancestor.getParent();
        }
        return null;
    }

    private static void distributeEvenly(SplitPane splitPane) {
        int itemCount = splitPane.getItems().size();
        double[] positions = new double[Math.max(0, itemCount - 1)];
        for (int index = 1; index < itemCount; index++) {
            positions[index - 1] = (double) index / itemCount;
        }
        splitPane.setDividerPositions(positions);
    }

    private void trackEmptySplitGroup(DetachableTabPane tabPane) {
        if (emptyGroupListeners.containsKey(tabPane))
            return;

        tabPane.setCloseIfEmpty(false);
        ListChangeListener<Tab> listener = _ -> {
            if (tabPane.getTabs().isEmpty()) {
                Platform.runLater(() -> removeEmptySplitPane(tabPane));
            }
        };
        tabPane.getTabs().addListener(listener);
        emptyGroupListeners.put(tabPane, listener);
    }

    private void removeEmptySplitPane(DetachableTabPane tabPane) {
        if (!tabPane.getTabs().isEmpty()
            || tabPane == primaryEditorPane
            || Boolean.TRUE.equals(tabPane.getProperties().get(EditorTabStripSupport.DRAG_ACTIVE_PROPERTY)))
            return;

        SplitPane splitPane = findContainingSplitPane(tabPane);
        if (!editorSplitPanes.contains(splitPane))
            return;

        splitPane.getItems().remove(tabPane);
        untrackEmptySplitGroup(tabPane);
        removeSelectionListener(tabPane);
        collapseEditorSplit(splitPane);
    }

    private void cleanupEmptyEditorGroups() {
        List.copyOf(emptyGroupListeners.keySet()).stream()
            .filter(pane -> pane.getTabs().isEmpty())
            .forEach(this::removeEmptySplitPane);
    }

    private void untrackEmptySplitGroup(DetachableTabPane tabPane) {
        ListChangeListener<Tab> listener = emptyGroupListeners.remove(tabPane);
        if (listener != null) {
            tabPane.getTabs().removeListener(listener);
        }
    }

    private void collapseEditorSplit(SplitPane splitPane) {
        if (splitPane.getItems().size() > 1) {
            distributeEvenly(splitPane);
            return;
        }

        SplitPane parentSplitPane = findContainingSplitPane(splitPane);
        Parent parent = splitPane.getParent();
        Node remaining = splitPane.getItems().isEmpty() ? null : splitPane.getItems().getFirst();
        if (remaining != null) {
            splitPane.getItems().remove(remaining);
        }
        editorSplitPanes.remove(splitPane);

        if (parentSplitPane != null) {
            int splitIndex = parentSplitPane.getItems().indexOf(splitPane);
            if (splitIndex < 0)
                return;

            if (remaining == null) {
                parentSplitPane.getItems().remove(splitIndex);
            } else {
                parentSplitPane.getItems().set(splitIndex, remaining);
            }

            if (editorSplitPanes.contains(parentSplitPane)) {
                collapseEditorSplit(parentSplitPane);
            }
        } else if (parent instanceof Pane parentPane) {
            int splitIndex = parentPane.getChildren().indexOf(splitPane);
            if (splitIndex < 0)
                return;

            if (remaining == null) {
                parentPane.getChildren().remove(splitIndex);
            } else {
                parentPane.getChildren().set(splitIndex, remaining);
            }
        } else if (parent == null
            && remaining instanceof Parent remainingRoot
            && splitPane.getScene() != null
            && splitPane.getScene().getRoot() == splitPane) {
            splitPane.getScene().setRoot(remainingRoot);
        }
    }

    private String nextEditorGroupId() {
        String groupId;
        do {
            groupId = "railroad:editor-group:" + ++editorGroupSequence;
        } while (editorGroupIds.containsValue(groupId));
        return groupId;
    }

    private void requireManaged(EditorTab tab) {
        Objects.requireNonNull(tab, "Tab cannot be null");
        if (openTabs.get(tab.documentId()) != tab)
            throw new IllegalArgumentException("Tab is not managed by this editor tab manager");
    }

    private List<EditorTab> tabsInSamePane(EditorTab tab) {
        TabPane tabPane = tab.tab().getTabPane();
        if (tabPane == null)
            return List.of();

        return tabPane.getTabs().stream()
            .map(tabsByControl::get)
            .filter(Objects::nonNull)
            .toList();
    }

    private int tabIndex(EditorTab tab) {
        TabPane tabPane = tab.tab().getTabPane();
        return tabPane == null ? -1 : tabPane.getTabs().indexOf(tab.tab());
    }

    private void closeInDescendingOrder(List<EditorTab> tabs) {
        tabs.stream()
            .sorted(Comparator.comparingInt(this::tabIndex).reversed())
            .forEach(this::requestClose);
    }

    private boolean requestClose(EditorTab editorTab) {
        if (openTabs.get(editorTab.documentId()) != editorTab)
            return false;

        Tab tab = editorTab.tab();
        pendingCloseSnapshots.put(editorTab.documentId(), captureClosedTab(editorTab));
        if (!IDETabLifecycle.requestClose(tab)) {
            pendingCloseSnapshots.remove(editorTab.documentId());
            return false;
        }

        return true;
    }

    private ClosedEditorTab captureClosedTab(EditorTab tab) {
        return ClosedEditorTab.capture(tab, Math.max(0, tabIndex(tab)));
    }

    private void reopen(ClosedEditorTab closedTab, boolean activate) {
        Project project = Services.IDE_STATE.getCurrentProject();
        if (project == null)
            return;

        IDEContentRouter.routeActive(WorkspaceContentTargets.CODE_EDITOR,
            tabPane -> reopenInTabPane(project, tabPane, closedTab, activate));
    }

    private EditorTab reopenInTabPane(
        Project project,
        DetachableTabPane tabPane,
        ClosedEditorTab closedTab,
        boolean activate
    ) {
        EditorTab reopenedTab = openInTabPane(
            project,
            closedTab.path(),
            tabPane,
            TabOpenRequest.reopened(closedTab, activate));
        if (reopenedTab == null)
            return null;

        recentlyClosedTabs.remove(closedTab);
        restoreViewState(reopenedTab, closedTab.viewState());
        return reopenedTab;
    }

    private static void restoreViewState(EditorTab tab, EditorViewState viewState) {
        TextEditorPane editor = tab.view().activeEditor();
        if (editor == null)
            return;

        viewState = Objects.requireNonNullElse(viewState, EditorViewState.EMPTY);
        int paragraphCount = editor.getParagraphs().size();
        for (EditorViewState.FoldRange fold : viewState.folds()) {
            if (paragraphCount < 2 || fold.startParagraph() >= paragraphCount - 1)
                continue;
            int start = Math.clamp(fold.startParagraph(), 0, paragraphCount - 2);
            int end = Math.clamp(fold.endParagraph(), start + 1, paragraphCount - 1);
            editor.foldParagraphs(start, end);
        }

        int documentLength = editor.getLength();
        int anchor = Math.clamp(viewState.anchorPosition(), 0, documentLength);
        int caret = Math.clamp(viewState.caretPosition(), 0, documentLength);
        editor.selectRange(anchor, caret);
        double horizontalScroll = viewState.horizontalScroll();
        double verticalScroll = viewState.verticalScroll();
        editor.scrollXToPixel(horizontalScroll);
        editor.scrollYToPixel(verticalScroll);
        Platform.runLater(() -> {
            if (tab.view().activeEditor() == editor) {
                editor.scrollXToPixel(horizontalScroll);
                editor.scrollYToPixel(verticalScroll);
            }
        });
    }
}
