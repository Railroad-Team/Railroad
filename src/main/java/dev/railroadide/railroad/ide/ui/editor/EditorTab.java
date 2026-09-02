package dev.railroadide.railroad.ide.ui.editor;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.language.EditorOpenView;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentIdentity;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.plugin.defaults.FileSystemDocument;
import dev.railroadide.railroad.plugin.spi.dto.Document;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRStackPane;
import dev.railroadide.railroad.ui.localized.LocalizedMenu;
import dev.railroadide.railroad.ui.localized.LocalizedMenuItem;
import dev.railroadide.railroad.ui.localized.LocalizedTooltip;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.beans.property.*;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.util.Duration;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.javafx.StackedFontIcon;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class EditorTab {
    private final ObjectProperty<DocumentIdentity> identity;
    private final Document document;
    private final Tab tab;
    private final EditorOpenView view;

    private final ObjectProperty<Path> path;
    private final StringProperty editorGroupId;
    private final BooleanProperty pinned;
    private final BooleanProperty preview;
    private final ReadOnlyObjectWrapper<EditorSaveState> saveState;
    private final ReadOnlyBooleanWrapper dirty;
    private final ReadOnlyBooleanWrapper saving;
    private final ReadOnlyBooleanWrapper saved;
    private final ReadOnlyBooleanWrapper saveFailed;

    EditorTab(
        DocumentIdentity identity,
        Document document,
        EditorOpenView view,
        String editorGroupId,
        boolean pinned,
        boolean preview) {
        this.identity = new SimpleObjectProperty<>(this, "identity", Objects.requireNonNull(identity));
        this.document = Objects.requireNonNull(document);
        Path path = document.getPath().toAbsolutePath().normalize();
        this.path = new SimpleObjectProperty<>(this, "path", path);
        this.editorGroupId = new SimpleStringProperty(
            this,
            "editorGroupId",
            Objects.requireNonNull(editorGroupId));
        this.view = Objects.requireNonNull(view);
        this.pinned = new SimpleBooleanProperty(this, "pinned", pinned);
        this.preview = new SimpleBooleanProperty(this, "preview", preview);
        this.saveState = new ReadOnlyObjectWrapper<>(this, "saveState", EditorSaveState.CLEAN);
        this.dirty = new ReadOnlyBooleanWrapper(this, "dirty");
        this.saving = new ReadOnlyBooleanWrapper(this, "saving");
        this.saved = new ReadOnlyBooleanWrapper(this, "saved");
        this.saveFailed = new ReadOnlyBooleanWrapper(this, "saveFailed");

        TextEditorPane editor = view.activeEditor();
        if (editor != null) {
            this.saveState.bind(editor.saveStateProperty());
        }
        this.dirty.bind(this.saveState.isNotEqualTo(EditorSaveState.CLEAN));
        this.saving.bind(this.saveState.isEqualTo(EditorSaveState.SAVING));
        this.saved.bind(this.saveState.isEqualTo(EditorSaveState.CLEAN));
        this.saveFailed.bind(this.saveState.isEqualTo(EditorSaveState.ERROR));
        if (document instanceof FileSystemDocument fileSystemDocument) {
            fileSystemDocument.setDirty(this.dirty.get());
            this.dirty.addListener((_, _, isDirty) -> fileSystemDocument.setDirty(isDirty));
        }

        this.tab = new Tab(path.getFileName().toString(), view.content());
        this.tab.setId("editor:" + identity.id());
        this.tab.getStyleClass().add("editor-tab");
        this.tab.setGraphic(createTabGraphic());
        this.tab.setClosable(false);
        this.tab.setContextMenu(createContextMenu());
    }

    public DocumentId documentId() {
        return identity().id();
    }

    public DocumentIdentity identity() {
        return identity.get();
    }

    public ReadOnlyObjectProperty<DocumentIdentity> identityProperty() {
        return identity;
    }

    public Document document() {
        return document;
    }

    public Tab tab() {
        return tab;
    }

    public Path path() {
        return path.get();
    }

    public ObjectProperty<Path> pathProperty() {
        return path;
    }

    public EditorOpenView view() {
        return view;
    }

    public String editorGroupId() {
        return editorGroupId.get();
    }

    public StringProperty editorGroupIdProperty() {
        return editorGroupId;
    }

    public boolean pinned() {
        return pinned.get();
    }

    public BooleanProperty pinnedProperty() {
        return pinned;
    }

    public boolean preview() {
        return preview.get();
    }

    public BooleanProperty previewProperty() {
        return preview;
    }

    public EditorSaveState saveState() {
        return saveState.get();
    }

    public ReadOnlyObjectProperty<EditorSaveState> saveStateProperty() {
        return saveState.getReadOnlyProperty();
    }

    public boolean dirty() {
        return dirty.get();
    }

    public ReadOnlyBooleanProperty dirtyProperty() {
        return dirty.getReadOnlyProperty();
    }

    public boolean saving() {
        return saving.get();
    }

    public ReadOnlyBooleanProperty savingProperty() {
        return saving.getReadOnlyProperty();
    }

    public boolean saved() {
        return saved.get();
    }

    public ReadOnlyBooleanProperty savedProperty() {
        return saved.getReadOnlyProperty();
    }

    public boolean saveFailed() {
        return saveFailed.get();
    }

    public ReadOnlyBooleanProperty saveFailedProperty() {
        return saveFailed.getReadOnlyProperty();
    }

    void rebind(DocumentIdentity identity, Path path) {
        this.identity.set(Objects.requireNonNull(identity));
        Path normalizedPath = Objects.requireNonNull(path).toAbsolutePath().normalize();
        this.path.set(normalizedPath);
        this.tab.setText(normalizedPath.getFileName().toString());
        this.tab.setId(normalizedPath.toString());
    }

    void setPinned(boolean pinned) {
        this.pinned.set(pinned);
    }

    void setEditorGroupId(String editorGroupId) {
        this.editorGroupId.set(Objects.requireNonNull(editorGroupId));
    }

    void setPreview(boolean preview) {
        this.preview.set(preview);
    }

    private RRHBox createTabGraphic() {
        var closeIcon = new FontIcon(FontAwesomeSolid.TIMES);
        closeIcon.getStyleClass().add("editor-tab-close-icon");

        var pinIcon = new FontIcon(FontAwesomeSolid.THUMBTACK);
        pinIcon.getStyleClass().add("editor-tab-pin-icon");

        var statusIcon = new FontIcon();
        statusIcon.getStyleClass().add("editor-tab-status-icon");
        var statusTooltip = new LocalizedTooltip("editor.tab.status.dirty");
        Tooltip.install(statusIcon, statusTooltip);
        var savingAnimation = new RotateTransition(Duration.seconds(1), statusIcon);
        savingAnimation.setByAngle(360);
        savingAnimation.setCycleCount(Animation.INDEFINITE);
        savingAnimation.setInterpolator(Interpolator.LINEAR);
        saveStateProperty().addListener(
            (_, _, state) -> updateSaveStatusIcon(statusIcon, statusTooltip, savingAnimation, state));
        updateSaveStatusIcon(statusIcon, statusTooltip, savingAnimation, saveState());

        var actionSlot = createIconSlot(closeIcon, "editor-tab-action-slot");
        actionSlot.getChildren().addAll(pinIcon, statusIcon);
        actionSlot.setMinSize(14, 14);
        actionSlot.setPrefSize(14, 14);
        actionSlot.setMaxSize(14, 14);
        actionSlot.setCursor(Cursor.HAND);
        closeIcon.visibleProperty().bind(
            pinnedProperty().not().and(savedProperty().or(actionSlot.hoverProperty())));
        pinIcon.visibleProperty().bind(pinnedProperty());
        statusIcon.visibleProperty().bind(
            dirtyProperty().and(pinnedProperty().or(actionSlot.hoverProperty().not())));
        pinnedProperty().addListener((_, _, _) -> updateCombinedActionStyle(actionSlot));
        dirtyProperty().addListener((_, _, _) -> updateCombinedActionStyle(actionSlot));
        updateCombinedActionStyle(actionSlot);
        actionSlot.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                if (pinned()) {
                    Services.EDITOR_TAB_MANAGER.togglePin(this);
                } else {
                    Services.EDITOR_TAB_MANAGER.close(this);
                }
                event.consume();
            }
        });
        var actionTooltip = new LocalizedTooltip(pinned()
            ? "editor.tab.contextmenu.unpin"
            : "editor.tab.contextmenu.close");
        pinnedProperty().addListener((_, _, isPinned) -> actionTooltip.setKey(isPinned
            ? "editor.tab.contextmenu.unpin"
            : "editor.tab.contextmenu.close"));
        Tooltip.install(actionSlot, actionTooltip);

        var graphic = new RRHBox(actionSlot);
        graphic.getStyleClass().removeAll("Railroad", "Pane", "HBox", "background-2");
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.getStyleClass().add("editor-tab-graphic");
        return graphic;
    }

    private static RRStackPane createIconSlot(FontIcon icon, String styleClass) {
        var slot = new RRStackPane(icon);
        slot.getStyleClass().removeAll("Railroad", "Pane", "StackPane", "background-2");
        slot.getStyleClass().add(styleClass);
        slot.setMinWidth(9);
        slot.setPrefWidth(9);
        slot.setMaxWidth(9);
        return slot;
    }

    private void updateCombinedActionStyle(RRStackPane actionSlot) {
        if (pinned() && dirty()) {
            if (!actionSlot.getStyleClass().contains("combined-status")) {
                actionSlot.getStyleClass().add("combined-status");
            }
        } else {
            actionSlot.getStyleClass().remove("combined-status");
        }
    }

    private static void updateSaveStatusIcon(
        FontIcon icon,
        LocalizedTooltip tooltip,
        RotateTransition savingAnimation,
        EditorSaveState state) {
        savingAnimation.stop();
        icon.setRotate(0);
        icon.getStyleClass().removeAll(
            "editor-tab-dirty-icon",
            "editor-tab-saving-icon",
            "editor-tab-save-failed-icon");
        if (state == EditorSaveState.ERROR) {
            icon.setIconCode(FontAwesomeSolid.EXCLAMATION_CIRCLE);
            icon.getStyleClass().add("editor-tab-save-failed-icon");
            tooltip.setKey("editor.tab.status.save_failed");
        } else if (state == EditorSaveState.SAVING) {
            icon.setIconCode(FontAwesomeSolid.SYNC_ALT);
            icon.getStyleClass().add("editor-tab-saving-icon");
            tooltip.setKey("editor.tab.status.saving");
            savingAnimation.playFromStart();
        } else {
            icon.setIconCode(FontAwesomeSolid.ASTERISK);
            icon.getStyleClass().add("editor-tab-dirty-icon");
            tooltip.setKey("editor.tab.status.dirty");
        }
    }

    ContextMenu createContextMenu() {
        var contextMenu = new ContextMenu();

        var pinUnpinIcon = new StackedFontIcon();
        pinUnpinIcon.setIconSize(16);
        var pinUnpin = new LocalizedMenuItem("editor.tab.contextmenu.pin", pinUnpinIcon);
        pinUnpin.setOnAction(_ -> Services.EDITOR_TAB_MANAGER.togglePin(this));
        pinnedProperty().addListener(
            (_, _, isPinned) -> updatePinMenuItem(pinUnpin, pinUnpinIcon, isPinned));
        updatePinMenuItem(pinUnpin, pinUnpinIcon, pinned());

        var close = new LocalizedMenuItem("editor.tab.contextmenu.close");
        close.setOnAction(_ -> Services.EDITOR_TAB_MANAGER.close(this));

        var closeOthers = new LocalizedMenuItem("editor.tab.contextmenu.close_others");
        closeOthers.setOnAction(_ -> Services.EDITOR_TAB_MANAGER.closeOthers(this));

        var closeAll = new LocalizedMenuItem("editor.tab.contextmenu.close_all");
        closeAll.setOnAction(_ -> Services.EDITOR_TAB_MANAGER.closeAll());

        var closeAllUnpinned = new LocalizedMenuItem("editor.tab.contextmenu.close_all_unpinned");
        closeAllUnpinned.setOnAction(_ -> Services.EDITOR_TAB_MANAGER.closeAllUnpinned());

        var closeToLeft = new LocalizedMenuItem("editor.tab.contextmenu.close_to_left");
        closeToLeft.setOnAction(_ -> Services.EDITOR_TAB_MANAGER.closeToLeft(this));

        var closeToRight = new LocalizedMenuItem("editor.tab.contextmenu.close_to_right");
        closeToRight.setOnAction(_ -> Services.EDITOR_TAB_MANAGER.closeToRight(this));

        var closeAllUnmodified = new LocalizedMenuItem("editor.tab.contextmenu.close_all_unmodified");
        closeAllUnmodified.setOnAction(_ -> Services.EDITOR_TAB_MANAGER.closeAllUnmodified());

        var closeAllSaved = new LocalizedMenuItem("editor.tab.contextmenu.close_all_saved");
        closeAllSaved.setOnAction(_ -> Services.EDITOR_TAB_MANAGER.closeAllSaved());

        var reopenClosedTab = new LocalizedMenuItem("editor.tab.contextmenu.reopen_closed_tab");
        reopenClosedTab.setOnAction(_ -> Services.EDITOR_TAB_MANAGER.reopenLastClosed());

        var copyPath = new LocalizedMenu("editor.tab.contextmenu.copy_path");
        var copyAbsolutePath = new LocalizedMenuItem("editor.tab.contextmenu.copy_absolute_path");
        copyAbsolutePath.setOnAction(_ -> copyToClipboard(path().toAbsolutePath().normalize().toString()));
        var copyProjectRelativePath = new LocalizedMenuItem("editor.tab.contextmenu.copy_project_relative_path");
        copyProjectRelativePath.setOnAction(_ -> {
            Path relativePath = projectRelativePath();
            if (relativePath != null) {
                copyToClipboard(relativePath.toString());
            }
        });
        copyPath.getItems().addAll(copyAbsolutePath, copyProjectRelativePath);

        var revealInFileExplorer = new LocalizedMenuItem("editor.tab.contextmenu.reveal_in_file_explorer");
        revealInFileExplorer.setOnAction(_ -> Services.EDITOR_TAB_MANAGER.revealInFileExplorer(this));

        var revealInProjectExplorer = new LocalizedMenuItem("editor.tab.contextmenu.reveal_in_project_explorer");
        revealInProjectExplorer.setOnAction(_ -> Services.EDITOR_TAB_MANAGER.revealInProjectExplorer(this));

        var openInTerminal = new LocalizedMenuItem("editor.tab.contextmenu.open_in_terminal");
        openInTerminal.setOnAction(_ -> Services.EDITOR_TAB_MANAGER.openInTerminal(this));

        var moveToPreviousGroup = new LocalizedMenuItem("editor.tab.contextmenu.move_to_previous_group");
        moveToPreviousGroup.setOnAction(_ -> Services.EDITOR_TAB_MANAGER.moveToPreviousGroup(this));

        var moveToNextGroup = new LocalizedMenuItem("editor.tab.contextmenu.move_to_next_group");
        moveToNextGroup.setOnAction(_ -> Services.EDITOR_TAB_MANAGER.moveToNextGroup(this));

        var splitRight = new LocalizedMenuItem("editor.tab.contextmenu.split_right");
        splitRight.setOnAction(_ -> Services.EDITOR_TAB_MANAGER.splitRight(this));

        var splitDown = new LocalizedMenuItem("editor.tab.contextmenu.split_down");
        splitDown.setOnAction(_ -> Services.EDITOR_TAB_MANAGER.splitDown(this));

        var openInNewWindow = new LocalizedMenuItem("editor.tab.contextmenu.open_in_new_window");
        openInNewWindow.setOnAction(_ -> Services.EDITOR_TAB_MANAGER.openInNewWindow(this));

        contextMenu.getItems().addAll(
            pinUnpin,
            new SeparatorMenuItem(),
            close,
            closeOthers,
            closeToRight,
            closeToLeft,
            new SeparatorMenuItem(),
            closeAll,
            closeAllUnpinned,
            closeAllUnmodified,
            closeAllSaved,
            reopenClosedTab,
            new SeparatorMenuItem(),
            copyPath,
            revealInFileExplorer,
            revealInProjectExplorer,
            openInTerminal,
            new SeparatorMenuItem(),
            moveToPreviousGroup,
            moveToNextGroup,
            splitRight,
            splitDown,
            openInNewWindow);

        contextMenu.setOnShowing(_ -> {
            closeOthers.setVisible(Services.EDITOR_TAB_MANAGER.hasOtherClosableTabs(this));
            closeToLeft.setVisible(Services.EDITOR_TAB_MANAGER.hasTabsToLeft(this));
            closeToRight.setVisible(Services.EDITOR_TAB_MANAGER.hasTabsToRight(this));
            moveToPreviousGroup.setVisible(Services.EDITOR_TAB_MANAGER.hasPreviousEditorGroup(this));
            moveToNextGroup.setVisible(Services.EDITOR_TAB_MANAGER.hasNextEditorGroup(this));
            copyProjectRelativePath.setDisable(projectRelativePath() == null);

            boolean pathExists = Files.exists(path());
            revealInFileExplorer.setDisable(!pathExists);
            revealInProjectExplorer.setDisable(!pathExists);
            openInTerminal.setDisable(!pathExists || path().getParent() == null);
        });

        return contextMenu;
    }

    private static void updatePinMenuItem(
        LocalizedMenuItem menuItem,
        StackedFontIcon icon,
        boolean pinned) {
        menuItem.setKey(pinned ? "editor.tab.contextmenu.unpin" : "editor.tab.contextmenu.pin");
        if (pinned) {
            icon.setIconCodes(FontAwesomeSolid.THUMBTACK);
        } else {
            icon.setIconCodes(FontAwesomeSolid.THUMBTACK, FontAwesomeSolid.SLASH);
        }
    }

    private Path projectRelativePath() {
        Project project = Services.IDE_STATE.getCurrentProject();
        if (project == null)
            return null;

        Path projectPath = project.getPath().toAbsolutePath().normalize();
        Path documentPath = path().toAbsolutePath().normalize();
        return documentPath.startsWith(projectPath) ? projectPath.relativize(documentPath) : null;
    }

    private static void copyToClipboard(String text) {
        var content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }
}
