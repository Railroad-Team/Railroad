package dev.railroadide.railroad.ide.sst.provider.api;

import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic.TextEditorDiagnostic;
import dev.railroadide.railroad.ide.sst.document.api.TextRange;

import java.util.List;
import java.util.Objects;

/**
 * Where code actions are requested: a caret or selection range of the captured document, together with the
 * diagnostics the host currently shows around it.
 * <p>
 * Diagnostics are a hint, not a guarantee: they may have been computed for an older revision. Hosts pass only
 * diagnostics whose location belongs to the captured revision, and providers must still verify the problem against
 * the captured document before offering a fix.
 *
 * @param range caret ({@code start == end}) or selection in the captured document
 * @param diagnostics diagnostics of the captured revision near the range
 */
public record CodeActionRequest(TextRange range, List<TextEditorDiagnostic> diagnostics) {
    /**
     * Validates a request and copies its diagnostics.
     *
     * @param range caret or selection
     * @param diagnostics diagnostics near the range
     */
    public CodeActionRequest {
        Objects.requireNonNull(range, "range");
        diagnostics = List.copyOf(diagnostics);
    }

    /**
     * Creates a request for a caret position without diagnostics.
     *
     * @param offset UTF-16 caret offset
     * @return caret request
     */
    public static CodeActionRequest at(int offset) {
        return new CodeActionRequest(new TextRange(offset, offset), List.of());
    }

    /**
     * Reports whether a half-open range touches the requested range. An empty requested range touches a range that
     * contains it or ends at it.
     *
     * @param start inclusive start offset
     * @param end exclusive end offset
     * @return whether the ranges touch
     */
    public boolean touches(int start, int end) {
        return start <= range.end && end >= range.start;
    }
}
