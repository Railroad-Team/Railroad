package dev.railroadide.railroad.ide.ui.editor;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.language.LanguageSupport;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.ide.language.impl.ImageLanguageSupport;
import dev.railroadide.railroad.ide.language.impl.PlainTextLanguageSupport;
import dev.railroadide.railroad.ide.sst.document.api.DocumentIdentity;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.ui.IDEContentRouter;
import dev.railroadide.railroad.ide.ui.IDETabLifecycle;
import dev.railroadide.railroad.ide.ui.IDEWelcomePane;
import dev.railroadide.railroad.ide.ui.WorkspaceContentTargets;
import dev.railroadide.railroad.plugin.defaults.FileSystemDocument;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.events.DocumentEvent;
import dev.railroadide.railroad.plugin.spi.events.ProjectEvent;
import dev.railroadide.railroad.plugin.spi.events.DocumentRenamedEvent;
import dev.railroadide.railroad.utility.FileUtils;
import dev.railroadide.railroad.utility.javafx.JavaFXUtils;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static dev.railroadide.railroad.ide.ui.editor.EditorTabSessionState.DEFAULT_EDITOR_GROUP_ID;

public class EditorTabManager {
    private final Map<DocumentId, EditorTab> openTabs = new LinkedHashMap<>();
    private final Deque<ClosedEditorTab> recentlyClosedTabs = new ArrayDeque<>();
    private final Map<Tab, EditorTab> tabsByControl = new IdentityHashMap<>();
    private final Map<DocumentId, ClosedEditorTab> pendingCloseSnapshots = new LinkedHashMap<>();
    private final Map<DetachableTabPane, ChangeListener<Tab>> selectionListeners = new IdentityHashMap<>();
    private final Map<DetachableTabPane, String> editorGroupIds = new IdentityHashMap<>();
    private boolean restoring;
    private boolean selectionUpdateScheduled;
    private EditorTab pendingSelection;
    private long selectionGeneration;

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
        Railroad.EVENT_BUS.subscribe(ProjectEvent.class, event -> {
            if (event.isClosed()) {
                if (selectionListeners.isEmpty()) {
                    resetTracking();
                } else {
                    JavaFXUtils.runOnApplicationThread(this::resetTracking);
                }
            }
        });
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
        editorTab.tab().addEventHandler(
            Tab.TAB_CLOSE_REQUEST_EVENT,
            _ -> pendingCloseSnapshots.put(editorTab.documentId(), captureClosedTab(editorTab)));
        editorTab.tab().addEventHandler(Tab.CLOSED_EVENT, _ -> handleClosed(editorTab));

        openTabs.put(editorTab.documentId(), editorTab);
        tabsByControl.put(editorTab.tab(), editorTab);
        ensureSelectionListener(tabPane, request.editorGroupId());
        Services.IDE_STATE.openDocument(document);
        addToTabPane(tabPane, editorTab.tab(), request.insertionIndex());

        if (request.activate()) {
            tabPane.getSelectionModel().select(editorTab.tab());
            activate(editorTab);
        }
        return editorTab;
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
    }

    private void ensureSelectionListener(DetachableTabPane tabPane, String editorGroupId) {
        editorGroupIds.putIfAbsent(tabPane, editorGroupId);
        if (selectionListeners.containsKey(tabPane))
            return;

        ChangeListener<Tab> listener = (_, _, selectedTab) -> queueSelectionUpdate(selectedTab);
        tabPane.getSelectionModel().selectedItemProperty().addListener(listener);
        selectionListeners.put(tabPane, listener);
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

        var editor = editorTab.view().activeEditor();
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
                .filter(identity -> !identity.id().equals(previousId))
                .orElseGet(() -> Services.IDE_STATE.rebindDocument(
                    editorTab.identity(),
                    DocumentUri.fromPath(normalizedTarget)));
            if (editorTab.document() instanceof FileSystemDocument fileSystemDocument) {
                fileSystemDocument.rebind(normalizedTarget);
            }
            replaceOpenTabIdentity(previousId, reboundIdentity.id(), editorTab);
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
            .map(view -> view.activeEditor())
            .filter(Objects::nonNull)
            .forEach(editor -> editor.discardChangesOnClose());
    }

    private boolean save(EditorTab editorTab) {
        var editor = editorTab.view().activeEditor();
        return editor == null || editor.saveNow();
    }

    private void replaceOpenTabIdentity(DocumentId previousId, DocumentId newId, EditorTab editorTab) {
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

    private static boolean pathsMatch(Path first, Path second) {
        if (first == null || second == null)
            return false;

        Path normalizedFirst = first.toAbsolutePath().normalize();
        Path normalizedSecond = second.toAbsolutePath().normalize();
        if (normalizedFirst.equals(normalizedSecond))
            return true;
        if (java.io.File.separatorChar == '\\'
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
        tabsByControl.clear();
        pendingCloseSnapshots.clear();
        openTabs.clear();
        recentlyClosedTabs.clear();
        Services.DOCUMENT_EDITOR_STATE.setActiveEditor(null, null);
    }

    public void close(EditorTab tab) {
        requireManaged(tab);
        requestClose(tab);
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
        var editor = tab.view().activeEditor();
        if (editor == null)
            return;

        int documentLength = editor.getLength();
        int anchor = Math.clamp(viewState.anchorPosition(), 0, documentLength);
        int caret = Math.clamp(viewState.caretPosition(), 0, documentLength);
        editor.selectRange(anchor, caret);
    }
}
