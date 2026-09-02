package dev.railroadide.railroad.ide.ui.editor;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.language.EditorOpenView;
import dev.railroadide.railroad.ide.language.LanguageSupport;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.ide.language.impl.ImageLanguageSupport;
import dev.railroadide.railroad.ide.language.impl.PlainTextLanguageSupport;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentIdentity;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.ui.IDEContentRouter;
import dev.railroadide.railroad.ide.ui.IDETabLifecycle;
import dev.railroadide.railroad.ide.ui.IDEWelcomePane;
import dev.railroadide.railroad.ide.ui.WorkspaceContentTargets;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.plugin.defaults.FileSystemDocument;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.events.DocumentEvent;
import dev.railroadide.railroad.plugin.spi.events.DocumentRenamedEvent;
import dev.railroadide.railroad.plugin.spi.events.ProjectEvent;
import dev.railroadide.railroad.settings.keybinds.KeybindHandler;
import dev.railroadide.railroad.settings.keybinds.Keybinds;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.utility.FileUtils;
import dev.railroadide.railroad.utility.javafx.JavaFXUtils;
import dev.railroadide.railroad.window.DialogBuilder;
import dev.railroadide.railroad.window.WindowBuilder;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.IntPredicate;

import static dev.railroadide.railroad.ide.ui.editor.EditorTabSessionState.DEFAULT_EDITOR_GROUP_ID;

@SuppressWarnings("resource")
public class EditorTabManager {
    private final Map<DocumentId, EditorTab> openTabs = new LinkedHashMap<>();
    private final Deque<ClosedEditorTab> recentlyClosedTabs = new ArrayDeque<>();
    private final Map<Tab, EditorTab> tabsByControl = new IdentityHashMap<>();
    private final Map<DocumentId, ClosedEditorTab> pendingCloseSnapshots = new LinkedHashMap<>();
    private final Map<DetachableTabPane, ChangeListener<Tab>> selectionListeners = new IdentityHashMap<>();
    private final Map<DetachableTabPane, EventHandler<MouseEvent>> mouseKeybindHandlers = new IdentityHashMap<>();
    private final Map<DetachableTabPane, String> editorGroupIds = new IdentityHashMap<>();
    private final Map<DetachableTabPane, ListChangeListener<Tab>> emptyGroupListeners = new IdentityHashMap<>();
    private final Map<DetachableTabPane, ListChangeListener<Tab>> tabOrderListeners = new IdentityHashMap<>();
    private final Set<DetachableTabPane> pendingTabOrderUpdates = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<SplitPane> editorSplitPanes = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<EditorTab, Stage> failedCloseDialogs = new IdentityHashMap<>();
    private final Set<EditorTab> discardApprovedTabs = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean restoring;
    private boolean selectionUpdateScheduled;
    private EditorTab pendingSelection;
    private long selectionGeneration;
    private long editorGroupSequence;

    public record SaveResult(List<EditorTab> failedTabs) {
        public SaveResult {
            failedTabs = List.copyOf(failedTabs);
        }

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
        int insertionIndex) {
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

    public EditorTabManager() {
        Railroad.EVENT_BUS.subscribe(ProjectEvent.class, this::handleProjectClosed);
        Railroad.EVENT_BUS.subscribe(DocumentRenamedEvent.class, this::handleRenamed);
        Railroad.EVENT_BUS.subscribe(DocumentEvent.class, this::handleDocumentEvent);
    }

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

    public void restoreSession(List<EditorTabSessionState> sessionState) {
        Objects.requireNonNull(sessionState, "Session state cannot be null");
        Project project = Services.IDE_STATE.getCurrentProject();
        if (project == null)
            return;

        List<EditorTabSessionState> tabsToRestore = sessionState.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingInt(EditorTabSessionState::order))
            .toList();
        IDEContentRouter.routeActive(WorkspaceContentTargets.CODE_EDITOR, tabPane -> {
            ensureSelectionListener(tabPane, DEFAULT_EDITOR_GROUP_ID);
            restoring = true;
            try {
                for (EditorTabSessionState tabState : tabsToRestore) {
                    try {
                        Services.IDE_STATE.restoreDocumentIdentity(tabState.identity());
                        if (tabState.path() == null) {
                            Railroad.LOGGER.warn("No editor provider can restore virtual document {}",
                                tabState.identity().uri());
                            continue;
                        }
                        openInTabPane(
                            project,
                            tabState.path(),
                            tabPane,
                            TabOpenRequest.restored(tabState));
                    } catch (RuntimeException exception) {
                        Railroad.LOGGER.error("Failed to restore editor tab for {}", tabState.path(), exception);
                    }
                }
            } finally {
                restoring = false;
            }

            EditorTab activeTab = tabsToRestore.stream()
                .filter(EditorTabSessionState::active)
                .map(EditorTabSessionState::path)
                .map(this::findOpen)
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(null);
            if (activeTab == null) {
                for (EditorTabSessionState tabState : tabsToRestore) {
                    activeTab = findOpen(tabState.path()).orElse(null);
                    if (activeTab != null)
                        break;
                }
            }
            if (activeTab != null) {
                tabPane.getSelectionModel().select(activeTab.tab());
            }
            activate(activeTab);
        });
    }

    private EditorTab openInTabPane(
        Project project,
        Path path,
        DetachableTabPane tabPane,
        TabOpenRequest request) {
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedPath)) {
            Railroad.LOGGER.warn("Cannot open missing or non-file path: {}", normalizedPath);
            return null;
        }

        EditorTab existingTab = findOpen(normalizedPath).orElse(null);
        if (existingTab != null) {
            if (request.applyPropertiesToExisting()) {
                existingTab.setPinned(request.pinned());
                existingTab.setPreview(request.preview());
                existingTab.setEditorGroupId(request.editorGroupId());
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
        var editorTab = new EditorTab(
            identity,
            document,
            editorOpenView,
            request.editorGroupId(),
            request.pinned(),
            request.preview());
        editorTab.tab().setId(normalizedPath.toString());
        editorTab.tab().addEventHandler(Tab.TAB_CLOSE_REQUEST_EVENT, event -> handleCloseRequest(editorTab, event));
        editorTab.tab().addEventHandler(Tab.CLOSED_EVENT, _ -> handleClosed(editorTab));

        openTabs.put(editorTab.documentId(), editorTab);
        tabsByControl.put(editorTab.tab(), editorTab);
        editorTab.pinnedProperty().addListener((_, _, _) -> keepPinnedTabsOnLeft(editorTab.tab().getTabPane()));
        ensureSelectionListener(tabPane, request.editorGroupId());
        Services.IDE_STATE.openDocument(document);
        addToTabPane(tabPane, editorTab.tab(), request.insertionIndex());

        if (request.activate()) {
            tabPane.getSelectionModel().select(editorTab.tab());
            activate(editorTab);
        }
        return editorTab;
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
            tabPane.getTabs().set(welcomeIndex, tab);
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

        ListChangeListener<Tab> listener = _ -> scheduleTabOrderUpdate(tabPane);
        tabPane.getTabs().addListener(listener);
        tabOrderListeners.put(tabPane, listener);
        keepPinnedTabsOnLeft(tabPane);
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

        Services.IDE_STATE.setActiveDocument(editorTab.document());
        Services.DOCUMENT_EDITOR_STATE.setActiveEditor(
            editorTab.view().activeEditor(),
            editorTab.view().languageId());
    }

    public Optional<EditorTab> activeTab() {
        EditorTab activeTab = Optional.ofNullable(Services.IDE_STATE.getActiveDocument())
            .map(Services.IDE_STATE::identifyDocument)
            .map(DocumentIdentity::id)
            .map(openTabs::get)
            .orElse(null);
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
            .ifPresent(tabPane -> tabPane.getSelectionModel().select(index));
    }

    /** Selects the last tab in the active editor group. */
    public void selectLastTab() {
        activeTab().map(EditorTab::tab)
            .map(Tab::getTabPane)
            .filter(tabPane -> !tabPane.getTabs().isEmpty())
            .ifPresent(tabPane -> tabPane.getSelectionModel().selectLast());
    }

    public SaveResult saveActive() {
        EditorTab activeTab = activeTab().orElse(null);
        if (activeTab == null || save(activeTab))
            return new SaveResult(List.of());
        return new SaveResult(List.of(activeTab));
    }

    public SaveResult saveAll() {
        List<EditorTab> failedTabs = openTabs.values().stream()
            .filter(EditorTab::dirty)
            .filter(tab -> !save(tab))
            .toList();
        return new SaveResult(failedTabs);
    }

    public boolean hasUnsavedChanges() {
        return openTabs.values().stream().anyMatch(EditorTab::dirty);
    }

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
            return true;
        } catch (RuntimeException exception) {
            editor.rebind(previousPath);
            Railroad.LOGGER.error("Failed to rebind editor from {} to {}", previousPath, normalizedTarget, exception);
            return false;
        }
    }

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

    public List<EditorTabSessionState> captureSessionState() {
        if (selectionUpdateScheduled) {
            applyPendingSelection(selectionGeneration);
        }

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
            editorTab.editorGroupId());
    }

    private void handleClosed(EditorTab editorTab) {
        if (openTabs.remove(editorTab.documentId()) == null)
            return;

        discardApprovedTabs.remove(editorTab);
        Stage failedCloseDialog = failedCloseDialogs.remove(editorTab);
        if (failedCloseDialog != null) {
            failedCloseDialog.close();
        }

        ClosedEditorTab closedTab = pendingCloseSnapshots.remove(editorTab.documentId());
        if (closedTab == null) {
            closedTab = captureClosedTab(editorTab);
        }
        recentlyClosedTabs.removeIf(tab -> tab.documentId().equals(editorTab.documentId()));
        recentlyClosedTabs.addFirst(closedTab);
        tabsByControl.remove(editorTab.tab());
        Services.IDE_STATE.closeDocument(editorTab.document());
        if (Services.IDE_STATE.getActiveDocument() == null) {
            Services.DOCUMENT_EDITOR_STATE.setActiveEditor(null, null);
        }
    }

    private void handleCloseRequest(EditorTab editorTab, Event event) {
        if (discardApprovedTabs.remove(editorTab)) {
            pendingCloseSnapshots.put(editorTab.documentId(), captureClosedTab(editorTab));
            return;
        }

        if (editorTab.dirty() && !save(editorTab)) {
            event.consume();
            showFailedCloseDialog(editorTab);
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
        });
        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            JavaFXUtils.runOnApplicationThread(update);
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
        pendingSelection = null;
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
        pendingTabOrderUpdates.clear();
        editorSplitPanes.clear();
        tabsByControl.clear();
        pendingCloseSnapshots.clear();
        List.copyOf(failedCloseDialogs.values()).forEach(Stage::close);
        failedCloseDialogs.clear();
        discardApprovedTabs.clear();
        openTabs.clear();
        recentlyClosedTabs.clear();
        Services.DOCUMENT_EDITOR_STATE.setActiveEditor(null, null);
    }

    public void close(EditorTab tab) {
        requireManaged(tab);
        requestClose(tab);
    }

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

    public void closeOthers(EditorTab tab) {
        requireManaged(tab);
        closeInDescendingOrder(tabsInSamePane(tab).stream()
            .filter(candidate -> candidate != tab && !candidate.pinned())
            .toList());
    }

    public void closeToRight(EditorTab tab) {
        requireManaged(tab);
        int tabIndex = tabIndex(tab);
        closeInDescendingOrder(tabsInSamePane(tab).stream()
            .filter(candidate -> tabIndex(candidate) > tabIndex && !candidate.pinned())
            .toList());
    }

    public void closeToLeft(EditorTab tab) {
        requireManaged(tab);
        int tabIndex = tabIndex(tab);
        closeInDescendingOrder(tabsInSamePane(tab).stream()
            .filter(candidate -> tabIndex(candidate) < tabIndex && !candidate.pinned())
            .toList());
    }

    public void closeAll() {
        closeInDescendingOrder(new ArrayList<>(openTabs.values()));
    }

    public void closeAllUnpinned() {
        closeInDescendingOrder(openTabs.values().stream()
            .filter(tab -> !tab.pinned())
            .toList());
    }

    public void closeAllUnmodified() {
        closeInDescendingOrder(openTabs.values().stream()
            .filter(tab -> !tab.dirty())
            .toList());
    }

    public void closeAllSaved() {
        closeInDescendingOrder(openTabs.values().stream()
            .filter(tab -> !tab.dirty())
            .toList());
    }

    public void pin(EditorTab tab) {
        Objects.requireNonNull(tab, "Tab cannot be null");
        if (openTabs.containsKey(tab.documentId())) {
            tab.setPinned(true);
        }
    }

    public void unpin(EditorTab tab) {
        Objects.requireNonNull(tab, "Tab cannot be null");
        if (openTabs.containsKey(tab.documentId())) {
            tab.setPinned(false);
        }
    }

    public void togglePin(EditorTab tab) {
        Objects.requireNonNull(tab, "Tab cannot be null");
        if (openTabs.containsKey(tab.documentId())) {
            tab.setPinned(!tab.pinned());
        }
    }

    public void setPreview(EditorTab tab, boolean preview) {
        Objects.requireNonNull(tab, "Tab cannot be null");
        if (openTabs.containsKey(tab.documentId())) {
            tab.setPreview(preview);
        }
    }

    public void reopenLastClosed() {
        ClosedEditorTab lastClosed = recentlyClosedTabs.peekFirst();
        if (lastClosed != null) {
            reopen(lastClosed, true);
        }
    }

    public void reopenClosed(ClosedEditorTab tab) {
        Objects.requireNonNull(tab, "Closed tab cannot be null");
        if (recentlyClosedTabs.contains(tab)) {
            reopen(tab, true);
        }
    }

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

    public void revealInFileExplorer(EditorTab tab) {
        Objects.requireNonNull(tab, "Tab cannot be null");
        if (!openTabs.containsKey(tab.documentId()))
            return;

        Path path = tab.path();
        if (path == null || !Files.exists(path))
            return;

        FileUtils.openInExplorer(path);
    }

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

    public void openInTerminal(EditorTab tab) {
        Objects.requireNonNull(tab, "Tab cannot be null");
        if (!openTabs.containsKey(tab.documentId()))
            return;

        Path path = tab.path();
        if (path == null || !Files.exists(path))
            return;

        FileUtils.openInTerminal(path.getParent());
    }

    public void moveToPreviousGroup(EditorTab tab) {
        moveToAdjacentGroup(tab, -1);
    }

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

    public void splitRight(EditorTab tab) {
        split(tab, Orientation.HORIZONTAL);
    }

    public void splitDown(EditorTab tab) {
        split(tab, Orientation.VERTICAL);
    }

    public boolean hasTabsToLeft(EditorTab tab) {
        return hasTabsBeside(tab, index -> index < tabIndex(tab));
    }

    public boolean hasTabsToRight(EditorTab tab) {
        return hasTabsBeside(tab, index -> index > tabIndex(tab));
    }

    public boolean hasOtherClosableTabs(EditorTab tab) {
        Objects.requireNonNull(tab, "Tab cannot be null");
        if (openTabs.get(tab.documentId()) != tab)
            return false;

        return tabsInSamePane(tab).stream()
            .anyMatch(candidate -> candidate != tab && !candidate.pinned());
    }

    public boolean hasPreviousEditorGroup(EditorTab tab) {
        return hasAdjacentEditorGroup(tab, -1);
    }

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

    private static DetachableTabPane createSiblingTabPane(DetachableTabPane sourceTabPane) {
        var sibling = new DetachableTabPane();
        sibling.setSceneFactory(sourceTabPane.getSceneFactory());
        sibling.setStageOwnerFactory(sourceTabPane.getStageOwnerFactory());
        sibling.setScope(sourceTabPane.getScope());
        sibling.setTabClosingPolicy(sourceTabPane.getTabClosingPolicy());
        sibling.setCloseIfEmpty(false);
        sibling.setDetachableTabPaneFactory(sourceTabPane.getDetachableTabPaneFactory());
        sibling.setStageFactory(sourceTabPane.getStageFactory());
        sibling.setDropHint(sourceTabPane.getDropHint());
        return sibling;
    }

    private boolean insertAdjacent(
        DetachableTabPane sourceTabPane,
        DetachableTabPane targetTabPane,
        Orientation orientation) {
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
        if (!tabPane.getTabs().isEmpty())
            return;

        SplitPane splitPane = findContainingSplitPane(tabPane);
        if (!editorSplitPanes.contains(splitPane))
            return;

        splitPane.getItems().remove(tabPane);
        untrackEmptySplitGroup(tabPane);
        removeSelectionListener(tabPane);
        collapseEditorSplit(splitPane);
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
        boolean activate) {
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

        int documentLength = editor.getLength();
        int anchor = Math.clamp(viewState.anchorPosition(), 0, documentLength);
        int caret = Math.clamp(viewState.caretPosition(), 0, documentLength);
        editor.selectRange(anchor, caret);
    }
}
