package dev.railroadide.railroad.ide.ui.editor;

import dev.railroadide.railroad.ide.language.EditorOpenView;
import dev.railroadide.railroad.ide.sst.document.api.DocumentIdentity;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.plugin.defaults.FileSystemDocument;
import dev.railroadide.railroad.plugin.spi.dto.Document;
import javafx.beans.property.*;
import javafx.scene.control.Tab;

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
        this.tab.closableProperty().bind(this.pinned.not());
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

}
