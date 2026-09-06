package dev.railroadide.railroad.ide.ui.editor;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.command.CommandContext;
import dev.railroadide.railroad.command.CommandMenuItems;
import dev.railroadide.railroad.command.Commands;
import dev.railroadide.railroad.ide.language.EditorOpenView;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentIdentity;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.plugin.defaults.FileSystemDocument;
import dev.railroadide.railroad.plugin.spi.dto.Document;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRStackPane;
import dev.railroadide.railroad.ui.localized.LocalizedMenu;
import dev.railroadide.railroad.ui.localized.LocalizedMenuItem;
import dev.railroadide.railroad.ui.localized.LocalizedTooltip;
import dev.railroadide.railroad.utility.FileUtils;
import dev.railroadide.railroad.utility.TimeFormatingUtils;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.*;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.util.Duration;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.javafx.StackedFontIcon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Owns a document view and its JavaFX tab, exposing observable identity, presentation, and save state.
 */
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
    private final Label titleLabel = new Label();
    private final RRStackPane fileIconSlot = new RRStackPane();
    private final Tooltip metadataTooltip = new Tooltip();
    private final InvalidationListener localizationListener = _ -> updateFilePresentation();
    private final WeakInvalidationListener weakLocalizationListener = new WeakInvalidationListener(
        localizationListener);

    /**
     * Creates a document tab and binds its presentation to the editor save state.
     *
     * @param identity logical identity of the document
     * @param document document displayed by the tab
     * @param view content view and optional text editor
     * @param editorGroupId identifier of the editor group containing the tab
     * @param pinned whether the tab is pinned against automatic eviction
     * @param preview whether the tab occupies the reusable preview slot
     */
    public EditorTab(
        DocumentIdentity identity,
        Document document,
        EditorOpenView view,
        String editorGroupId,
        boolean pinned,
        boolean preview
    ) {
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
        this.tab.setTooltip(metadataTooltip);
        this.tab.setClosable(false);
        this.tab.setContextMenu(createContextMenu());
        this.path.addListener((_, _, _) -> updateFilePresentation());
        this.saveState.addListener((_, _, _) -> updateMetadataTooltip());
        this.pinned.addListener((_, _, isPinned) -> {
            updateTabStateStyle("pinned", isPinned);
            updateMetadataTooltip();
        });
        this.preview.addListener((_, _, isPreview) -> {
            updateTabStateStyle("preview", isPreview);
            updateMetadataTooltip();
        });
        this.saveFailed.addListener((_, _, needsAttention) -> updateTabStateStyle("attention", needsAttention));
        L18n.currentLanguageProperty().addListener(weakLocalizationListener);
        updateTabStateStyle("pinned", pinned());
        updateTabStateStyle("preview", preview());
        updateTabStateStyle("attention", saveFailed());
        updateFilePresentation();
    }

    /**
     * Returns the logical document identifier.
     *
     * @return document identifier independent of its current path
     */
    public DocumentId documentId() {
        return identity().id();
    }

    /**
     * Returns the current document identity.
     *
     * @return logical document identity
     */
    public DocumentIdentity identity() {
        return identity.get();
    }

    /**
     * Exposes changes to the document identity.
     *
     * @return observable document identity
     */
    public ReadOnlyObjectProperty<DocumentIdentity> identityProperty() {
        return identity;
    }

    /**
     * Returns the document displayed by this tab.
     *
     * @return backing document model
     */
    public Document document() {
        return document;
    }

    /**
     * Returns the JavaFX control presenting the document view.
     *
     * @return tab control
     */
    public Tab tab() {
        return tab;
    }

    /**
     * Returns the current document path.
     *
     * @return document file path
     */
    public Path path() {
        return path.get();
    }

    /**
     * Exposes the document path for observation and updates.
     *
     * @return mutable document path property
     */
    public ObjectProperty<Path> pathProperty() {
        return path;
    }

    /**
     * Returns the document content view.
     *
     * @return view containing the content node and optional text editor
     */
    public EditorOpenView view() {
        return view;
    }

    /**
     * Returns the containing editor group identifier.
     *
     * @return editor group identifier
     */
    public String editorGroupId() {
        return editorGroupId.get();
    }

    /**
     * Exposes the containing editor group identifier.
     *
     * @return mutable editor group property
     */
    public StringProperty editorGroupIdProperty() {
        return editorGroupId;
    }

    /**
     * Reports whether the tab is pinned.
     *
     * @return true when the tab is pinned
     */
    public boolean pinned() {
        return pinned.get();
    }

    /**
     * Exposes whether the tab is pinned.
     *
     * @return mutable pinned-state property
     */
    public BooleanProperty pinnedProperty() {
        return pinned;
    }

    /**
     * Reports whether the tab occupies the reusable preview slot.
     *
     * @return true for a temporary preview tab
     */
    public boolean preview() {
        return preview.get();
    }

    /**
     * Exposes whether the tab is a temporary preview.
     *
     * @return mutable preview-state property
     */
    public BooleanProperty previewProperty() {
        return preview;
    }

    /**
     * Returns the current save state.
     *
     * @return current editor save state
     */
    public EditorSaveState saveState() {
        return saveState.get();
    }

    /**
     * Exposes changes to the editor save state.
     *
     * @return read-only observable save state
     */
    public ReadOnlyObjectProperty<EditorSaveState> saveStateProperty() {
        return saveState.getReadOnlyProperty();
    }

    /**
     * Reports whether the editor is in any state other than clean.
     *
     * @return true when the save state is not CLEAN
     */
    public boolean dirty() {
        return dirty.get();
    }

    /**
     * Exposes changes to whether the editor is not clean.
     *
     * @return read-only observable dirty flag
     */
    public ReadOnlyBooleanProperty dirtyProperty() {
        return dirty.getReadOnlyProperty();
    }

    /**
     * Reports whether a save is in progress.
     *
     * @return true when the save state is SAVING
     */
    public boolean saving() {
        return saving.get();
    }

    /**
     * Exposes changes to whether a save is in progress.
     *
     * @return read-only observable saving flag
     */
    public ReadOnlyBooleanProperty savingProperty() {
        return saving.getReadOnlyProperty();
    }

    /**
     * Reports whether the editor is clean.
     *
     * @return true when the save state is CLEAN
     */
    public boolean saved() {
        return saved.get();
    }

    /**
     * Exposes changes to whether the editor is clean.
     *
     * @return read-only observable clean-state flag
     */
    public ReadOnlyBooleanProperty savedProperty() {
        return saved.getReadOnlyProperty();
    }

    /**
     * Reports whether the latest save failed.
     *
     * @return true when the save state is ERROR
     */
    public boolean saveFailed() {
        return saveFailed.get();
    }

    /**
     * Returns the document language identifier.
     *
     * @return language identifier used to resolve language support
     */
    public String languageId() {
        return document.getLanguageId();
    }

    /**
     * Resolves a localized language name with a language-support fallback.
     *
     * @return display name of the document language
     */
    public String languageDisplayName() {
        String displayName = LanguageSupportRegistry.get(languageId())
            .map(support -> support.displayName())
            .orElseGet(() -> switch (languageId()) {
                case "image" -> "Image";
                case "plaintext" -> "Plain Text";
                case "binary" -> "Binary";
                default -> languageId();
            });
        String localizationKey = "railroad.language.name." + languageId();
        return L18n.hasTranslation(localizationKey) ? L18n.localize(localizationKey) : displayName;
    }

    /**
     * Returns the title currently shown in the tab header.
     *
     * @return displayed tab title
     */
    public String displayTitle() {
        return titleLabel.getText();
    }

    /**
     * Exposes changes to the title shown in the tab header.
     *
     * @return observable title property
     */
    public ReadOnlyStringProperty displayTitleProperty() {
        return titleLabel.textProperty();
    }

    /**
     * Exposes changes to whether the latest save failed.
     *
     * @return read-only observable save-failure flag
     */
    public ReadOnlyBooleanProperty saveFailedProperty() {
        return saveFailed.getReadOnlyProperty();
    }

    /**
     * Updates the document identity and normalized absolute file path.
     *
     * @param identity logical identity of the document
     * @param path path of the document file
     */
    public void rebind(DocumentIdentity identity, Path path) {
        this.identity.set(Objects.requireNonNull(identity));
        Path normalizedPath = Objects.requireNonNull(path).toAbsolutePath().normalize();
        this.path.set(normalizedPath);
        this.tab.setId(normalizedPath.toString());
    }

    /**
     * Updates the tab title, header label, and accessible description.
     *
     * @param displayTitle title displayed in the tab header and accessible text
     */
    public void setDisplayTitle(String displayTitle) {
        String title = Objects.requireNonNull(displayTitle, "Display title cannot be null");
        this.tab.setText(title);
        this.titleLabel.setText(title);
        updateAccessibleText();
    }

    /**
     * Updates the pinned flag and its bound presentation.
     *
     * @param pinned whether the tab is pinned against automatic eviction
     */
    public void setPinned(boolean pinned) {
        this.pinned.set(pinned);
    }

    /**
     * Updates the identifier of the group containing this tab.
     *
     * @param editorGroupId identifier of the editor group containing the tab
     */
    public void setEditorGroupId(String editorGroupId) {
        this.editorGroupId.set(Objects.requireNonNull(editorGroupId));
    }

    /**
     * Updates the temporary preview flag and its bound presentation.
     *
     * @param preview whether the tab occupies the reusable preview slot
     */
    public void setPreview(boolean preview) {
        this.preview.set(preview);
    }

    private void updateTabStateStyle(String styleClass, boolean enabled) {
        if (enabled) {
            if (!tab.getStyleClass().contains(styleClass)) {
                tab.getStyleClass().add(styleClass);
            }
        } else {
            tab.getStyleClass().remove(styleClass);
        }
    }

    private RRHBox createTabGraphic() {
        fileIconSlot.getStyleClass().removeAll("Railroad", "Pane", "StackPane", "background-2");
        fileIconSlot.getStyleClass().add("editor-tab-file-icon");
        fileIconSlot.setMinSize(22, 22);
        fileIconSlot.setPrefSize(22, 22);
        fileIconSlot.setMaxSize(22, 22);
        titleLabel.getStyleClass().add("editor-tab-title");

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

        var graphic = new RRHBox(6, fileIconSlot, titleLabel, actionSlot);
        graphic.getStyleClass().removeAll("Railroad", "Pane", "HBox", "background-2");
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.getStyleClass().add("editor-tab-graphic");
        return graphic;
    }

    private void updateFilePresentation() {
        Path editorPath = path();
        var icon = FileUtils.getIcon(editorPath);
        icon.getStyleClass().add("editor-tab-file-type-icon");
        icon.setAccessibleRole(AccessibleRole.TEXT);
        icon.setAccessibleText(languageDisplayName() + " file");
        fileIconSlot.getChildren().setAll(icon);
        if (titleLabel.getText() == null || titleLabel.getText().isBlank()) {
            setDisplayTitle(EditorTabPresentation.fileName(editorPath));
        }
        updateMetadataTooltip();
    }

    private void updateMetadataTooltip() {
        Path editorPath = path().toAbsolutePath().normalize();
        StringBuilder text = new StringBuilder(editorPath.toString())
            .append('\n').append(L18n.localize("editor.tab.tooltip.language", languageDisplayName()))
            .append('\n').append(L18n.localize("editor.tab.tooltip.save_state", saveStateText()));
        if (Files.isRegularFile(editorPath)) {
            text.append('\n').append(L18n.localize(
                "editor.tab.tooltip.size",
                FileUtils.humanReadableByteCount(editorPath)));
            try {
                text.append('\n').append(L18n.localize(
                    "editor.tab.tooltip.modified",
                    TimeFormatingUtils.formatDateTime(Files.getLastModifiedTime(editorPath).toMillis())));
            } catch (IOException _) {
                // The file can disappear while its tab is still open.
            }
            if (!Files.isWritable(editorPath)) {
                text.append('\n').append(L18n.localize("editor.tab.tooltip.read_only"));
            }
        } else {
            text.append('\n').append(L18n.localize("editor.tab.tooltip.missing"));
        }
        if (pinned()) {
            text.append('\n').append(L18n.localize("editor.tab.tooltip.pinned"));
        }
        if (preview()) {
            text.append('\n').append(L18n.localize("editor.tab.tooltip.preview"));
        }
        metadataTooltip.setText(text.toString());
        updateAccessibleText();
    }

    private String saveStateText() {
        return switch (saveState()) {
            case CLEAN -> L18n.localize("editor.tab.status.saved");
            case DIRTY -> L18n.localize("editor.tab.status.dirty");
            case SAVING -> L18n.localize("editor.tab.status.saving");
            case ERROR -> L18n.localize("editor.tab.status.save_failed");
        };
    }

    private void updateAccessibleText() {
        if (tab.getGraphic() != null) {
            tab.getGraphic().setAccessibleText(displayTitle() + ", " + languageDisplayName() + ", " + saveStateText()
                + ", " + path().toAbsolutePath().normalize());
        }
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
        EditorSaveState state
    ) {
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

    /**
     * Creates the tab menu for closing, pinning, navigation, and group actions.
     *
     * @return new context menu for this tab
     */
    public ContextMenu createContextMenu() {
        var contextMenu = new ContextMenu();

        var pinUnpinIcon = new StackedFontIcon();
        pinUnpinIcon.setIconSize(16);
        var pinUnpin = new LocalizedMenuItem("editor.tab.contextmenu.pin", pinUnpinIcon);
        CommandMenuItems.bind(pinUnpin, Commands.TOGGLE_PIN_EDITOR_TAB, this::editorTabCommandContext);
        pinnedProperty().addListener(
            (_, _, isPinned) -> updatePinMenuItem(pinUnpin, pinUnpinIcon, isPinned));
        updatePinMenuItem(pinUnpin, pinUnpinIcon, pinned());

        var close = CommandMenuItems.create(
            Commands.CLOSE_EDITOR_TAB,
            this::editorTabCommandContext);

        var closeOthers = CommandMenuItems.create(
            Commands.CLOSE_OTHER_EDITOR_TABS,
            this::editorTabCommandContext);

        var closeAll = CommandMenuItems.create(
            Commands.CLOSE_ALL_EDITOR_TABS,
            this::commandContext);

        var closeAllUnpinned = CommandMenuItems.create(
            Commands.CLOSE_ALL_UNPINNED_EDITOR_TABS,
            this::commandContext);

        var closeToLeft = CommandMenuItems.create(
            Commands.CLOSE_EDITOR_TABS_TO_LEFT,
            this::editorTabCommandContext);

        var closeToRight = CommandMenuItems.create(
            Commands.CLOSE_EDITOR_TABS_TO_RIGHT,
            this::editorTabCommandContext);

        var closeAllUnmodified = CommandMenuItems.create(
            Commands.CLOSE_ALL_UNMODIFIED_EDITOR_TABS,
            this::commandContext);

        var closeAllSaved = CommandMenuItems.create(
            Commands.CLOSE_ALL_SAVED_EDITOR_TABS,
            this::commandContext);

        var reopenClosedTab = CommandMenuItems.create(
            Commands.REOPEN_CLOSED_EDITOR_TAB,
            this::commandContext);

        var copyPath = new LocalizedMenu("editor.tab.contextmenu.copy_path");
        var copyAbsolutePath = CommandMenuItems.create(
            Commands.COPY_EDITOR_TAB_ABSOLUTE_PATH,
            this::editorTabCommandContext);
        var copyProjectRelativePath = CommandMenuItems.create(
            Commands.COPY_EDITOR_TAB_PROJECT_RELATIVE_PATH,
            this::editorTabCommandContext);
        copyPath.getItems().addAll(copyAbsolutePath, copyProjectRelativePath);

        var revealInFileExplorer = CommandMenuItems.create(
            Commands.REVEAL_EDITOR_TAB_IN_FILE_EXPLORER,
            this::editorTabCommandContext);

        var revealInProjectExplorer = CommandMenuItems.create(
            Commands.REVEAL_EDITOR_TAB_IN_PROJECT_EXPLORER,
            this::editorTabCommandContext);

        var openInTerminal = CommandMenuItems.create(
            Commands.OPEN_EDITOR_TAB_IN_TERMINAL,
            this::editorTabCommandContext);

        var moveToPreviousGroup = CommandMenuItems.create(
            Commands.MOVE_EDITOR_TAB_TO_PREVIOUS_GROUP,
            this::editorTabCommandContext);

        var moveToNextGroup = CommandMenuItems.create(
            Commands.MOVE_EDITOR_TAB_TO_NEXT_GROUP,
            this::editorTabCommandContext);

        var splitRight = CommandMenuItems.create(
            Commands.SPLIT_EDITOR_TAB_RIGHT,
            this::editorTabCommandContext);

        var splitDown = CommandMenuItems.create(
            Commands.SPLIT_EDITOR_TAB_DOWN,
            this::editorTabCommandContext);

        var openInNewWindow = CommandMenuItems.create(
            Commands.OPEN_EDITOR_TAB_IN_NEW_WINDOW,
            this::editorTabCommandContext);

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
        });

        return contextMenu;
    }

    private static void updatePinMenuItem(
        LocalizedMenuItem menuItem,
        StackedFontIcon icon,
        boolean pinned
    ) {
        menuItem.setKey(pinned ? "editor.tab.contextmenu.unpin" : "editor.tab.contextmenu.pin");
        if (pinned) {
            icon.setIconCodes(FontAwesomeSolid.THUMBTACK);
        } else {
            icon.setIconCodes(FontAwesomeSolid.THUMBTACK, FontAwesomeSolid.SLASH);
        }
    }

    private CommandContext<Void> commandContext() {
        return CommandContext.forProject(
            Services.IDE_STATE.getCurrentProject(),
            view.content());
    }

    private CommandContext<EditorTab> editorTabCommandContext() {
        return CommandContext.withArgument(
            Services.IDE_STATE.getCurrentProject(),
            view.content(),
            this);
    }

}
