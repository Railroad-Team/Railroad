package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.sst.document.api.DocumentStore;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.plugin.spi.editor.EditorSubscription;
import dev.railroadide.railroad.plugin.spi.editor.TextEditorAccess;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Owns presentation resources and exposes an optional, implementation-independent text capability. */
public final class EditorOpenView {
    private final Node content;
    private final ObservableValue<TextEditorPane> source;
    private final TextEditorAccess textEditor;
    private final String languageId;
    private final Consumer<DocumentStore> attachStore;
    private final Consumer<Path> rebind;
    private final Runnable closeResources;
    private final List<EditorSubscription> sourceSubscriptions = new ArrayList<>();
    private boolean closed;

    /**
     * Creates a direct text editor or non-text view. The tab manager owns source disposal.
     *
     * @param content displayed content
     * @param activeEditor source control, or null for non-text content
     * @param languageId stable language identifier
     */
    public EditorOpenView(Node content, @Nullable TextEditorPane activeEditor, String languageId) {
        this(content, new ReadOnlyObjectWrapper<>(activeEditor).getReadOnlyProperty(),
            activeEditor == null ? null : activeEditor.textEditorAccess(), languageId, _ -> {
            }, _ -> {
            }, () -> {
            });
    }

    /**
     * Creates a wrapped or lazy text view with explicit presentation lifecycle hooks.
     *
     * @param content displayed content
     * @param source internal source ownership observable
     * @param textEditor public text capability, or null when unsupported
     * @param languageId stable language identifier
     * @param attachStore initializes wrapper observation after source initialization
     * @param rebind updates the wrapper's location
     * @param closeResources releases wrapper resources after accepted source closure
     */
    public EditorOpenView(
        Node content,
        ObservableValue<TextEditorPane> source,
        @Nullable TextEditorAccess textEditor,
        String languageId,
        Consumer<DocumentStore> attachStore,
        Consumer<Path> rebind,
        Runnable closeResources
    ) {
        this.content = Objects.requireNonNull(content);
        this.source = Objects.requireNonNull(source);
        this.textEditor = textEditor;
        this.languageId = Objects.requireNonNull(languageId);
        this.attachStore = Objects.requireNonNull(attachStore);
        this.rebind = Objects.requireNonNull(rebind);
        this.closeResources = Objects.requireNonNull(closeResources);
    }

    /**
     * Returns the presentation node owned by this view.
     *
     * @return displayed content
     */
    public Node content() {
        return content;
    }

    /**
     * Returns the language associated with this view.
     *
     * @return stable language identifier
     */
    public String languageId() {
        return languageId;
    }

    /**
     * Discovers text operations without materializing a lazy source editor.
     *
     * @return text capability, or empty when unsupported or the view is closed
     */
    public Optional<TextEditorAccess> textEditor() {
        return closed ? Optional.empty() : Optional.ofNullable(textEditor);
    }

    /**
     * Returns the current source control for internal save, layout and lifecycle ownership.
     *
     * @return source control, or {@code null} when no source editor is materialized
     */
    public @Nullable TextEditorPane activeEditor() {
        return source.getValue();
    }

    /**
     * Registers an internal source listener released with the view.
     *
     * @param listener source creation or removal callback
     */
    public void observeSourceEditor(Consumer<TextEditorPane> listener) {
        if (closed)
            throw new IllegalStateException("Editor view is closed");
        ChangeListener<TextEditorPane> change = (_, _, current) -> listener.accept(current);
        source.addListener(change);
        sourceSubscriptions.add(() -> source.removeListener(change));
    }

    /**
     * Initializes wrapper observation using the project's document store.
     *
     * @param store project store already attached to any materialized source
     */
    public void setDocumentStore(DocumentStore store) {
        attachStore.accept(store);
    }

    /**
     * Updates the wrapper's location after its source has been rebound.
     *
     * @param path new location of this view
     */
    public void rebind(Path path) {
        rebind.accept(path);
    }

    /** Releases listeners and wrapper resources after accepted source closure; safe to repeat. */
    public void closeResources() {
        if (closed)
            return;
        closed = true;
        sourceSubscriptions.forEach(EditorSubscription::close);
        sourceSubscriptions.clear();
        closeResources.run();
    }
}
