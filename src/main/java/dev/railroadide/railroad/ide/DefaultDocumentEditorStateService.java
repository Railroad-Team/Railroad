package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.ide.sst.document.api.TextPosition;
import dev.railroadide.railroad.plugin.spi.editor.EditorSubscription;
import dev.railroadide.railroad.plugin.spi.editor.TextEditorAccess;
import dev.railroadide.railroad.plugin.spi.services.DocumentEditorStateService;
import dev.railroadide.railroad.plugin.spi.state.Cursor;
import dev.railroadide.railroad.plugin.spi.state.Selection;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Exposes the active managed text target and its cursor, selection, and language state. */
@Slf4j
public class DefaultDocumentEditorStateService implements DocumentEditorStateService {
    private TextEditorAccess activeEditor;
    private String activeLanguageId = "";
    private final List<ActiveSubscription> subscriptions = new ArrayList<>();
    private long generation;

    @Override
    public void setActiveEditor(@Nullable TextEditorAccess editor, @Nullable String languageId) {
        activeLanguageId = editor != null && languageId != null ? languageId : "";
        if (activeEditor == editor)
            return;
        activeEditor = editor;

        long currentGeneration = ++generation;
        var current = getActiveTextEditor();
        for (ActiveSubscription subscription : List.copyOf(subscriptions)) {
            if (currentGeneration != generation)
                break;
            if (!subscription.closed) {
                try {
                    subscription.listener.accept(current);
                } catch (RuntimeException exception) {
                    log.warn("Active text editor listener failed", exception);
                }
            }
        }
    }

    @Override
    public List<Cursor> getCursors() {
        if (activeEditor == null)
            return List.of();

        var snapshot = activeEditor.snapshot();
        return List.of(cursor(snapshot.lineMap().positionAt(activeEditor.selection().caret())));
    }

    @Override
    public List<Selection> getSelections() {
        if (activeEditor == null)
            return List.of();

        var selection = activeEditor.selection();
        int start = Math.min(selection.anchor(), selection.caret());
        int end = Math.max(selection.anchor(), selection.caret());
        if (start == end)
            return List.of();

        var map = activeEditor.snapshot().lineMap();
        return List.of(new Selection(cursor(map.positionAt(start)), cursor(map.positionAt(end))));
    }

    @Override
    public String getLanguageId() {
        return activeLanguageId;
    }

    @Override
    public Optional<TextEditorAccess> getActiveTextEditor() {
        return Optional.ofNullable(activeEditor);
    }

    @Override
    public EditorSubscription observeActiveTextEditor(Consumer<Optional<TextEditorAccess>> listener) {
        var subscription = new ActiveSubscription(Objects.requireNonNull(listener, "listener"));
        subscriptions.add(subscription);
        try {
            listener.accept(getActiveTextEditor());
        } catch (RuntimeException exception) {
            subscription.close();
            throw exception;
        }
        return subscription;
    }

    private static Cursor cursor(TextPosition position) {
        return new Cursor(position.line(), position.column());
    }

    private final class ActiveSubscription implements EditorSubscription {
        private final Consumer<Optional<TextEditorAccess>> listener;
        private boolean closed;

        private ActiveSubscription(Consumer<Optional<TextEditorAccess>> listener) {
            this.listener = listener;
        }

        @Override
        public void close() {
            if (closed)
                return;
            closed = true;
            subscriptions.remove(this);
        }
    }
}
