package dev.railroadide.railroad.plugin.spi.editor;

import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.TextRange;
import dev.railroadide.railroad.plugin.spi.services.DocumentEditorStateService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A deliberately small feature fixture that depends only on the public text editor API. */
public final class FindReplaceConsumer implements AutoCloseable {
    private final EditorSubscription activeSubscription;
    private EditorSubscription documentSubscription;
    private TextEditorAccess target;
    private TextDocumentSnapshot searched;
    private List<TextRange> matches = List.of();
    private int notifications;

    public FindReplaceConsumer(DocumentEditorStateService editors) {
        activeSubscription = editors.observeActiveTextEditor(this::retarget);
    }

    private void retarget(Optional<TextEditorAccess> active) {
        if (documentSubscription != null) {
            documentSubscription.close();
            documentSubscription = null;
        }
        target = active.orElse(null);
        searched = null;
        matches = List.of();
    }

    public List<TextRange> find(String needle) {
        if (target == null || needle.isEmpty())
            return List.of();
        target.reveal();
        if (documentSubscription == null) {
            documentSubscription = target.observe(_ -> notifications++);
        }
        searched = target.snapshot();
        var found = new ArrayList<TextRange>();
        for (int offset = searched.text().indexOf(needle); offset >= 0; offset = searched.text().indexOf(needle,
            offset + needle.length())) {
            found.add(new TextRange(offset, offset + needle.length()));
        }
        matches = List.copyOf(found);
        return matches;
    }

    public EditResult select(int index) {
        TextRange match = matches.get(index);
        return target.selectAndReveal(searched, new TextSelection(match.start, match.end));
    }

    public EditResult replaceAll(String replacement) {
        return target.replace(searched,
            matches.stream().map(range -> new TextReplacement(range, replacement)).toList());
    }

    public int notifications() {
        return notifications;
    }

    @Override
    public void close() {
        activeSubscription.close();
        if (documentSubscription != null) {
            documentSubscription.close();
        }
        target = null;
    }
}
