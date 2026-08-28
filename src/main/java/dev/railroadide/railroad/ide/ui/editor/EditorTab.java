package dev.railroadide.railroad.ide.ui.editor;

import dev.railroadide.railroad.ide.language.EditorOpenView;
import dev.railroadide.railroad.ide.sst.document.api.DocumentIdentity;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
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

    void rebind(DocumentIdentity identity, Path path) {
        this.identity.set(Objects.requireNonNull(identity));
        this.path.set(Objects.requireNonNull(path));
        this.tab.setText(path.getFileName().toString());
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

    void setSaveState(EditorSaveState saveState) {
        this.saveState.set(Objects.requireNonNull(saveState));
    }
}
