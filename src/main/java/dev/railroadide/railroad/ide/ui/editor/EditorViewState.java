package dev.railroadide.railroad.ide.ui.editor;

import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stores editor selection, scroll position, and folded paragraph ranges.
 *
 * @param caretPosition caret offset in the document text
 * @param anchorPosition selection anchor offset in the document text
 * @param horizontalScroll estimated horizontal scroll offset
 * @param verticalScroll estimated vertical scroll offset
 * @param folds folded paragraph ranges to restore
 */
public record EditorViewState(
    int caretPosition,
    int anchorPosition,
    double horizontalScroll,
    double verticalScroll,
    List<FoldRange> folds
) {
    /**
     * Default view state at the start of the document with no folds.
     */
    public static final EditorViewState EMPTY = new EditorViewState(0, 0, 0.0, 0.0, List.of());

    /**
     * Creates view state with nonnegative positions, valid scroll offsets, and an immutable fold list.
     *
     * @param caretPosition caret offset in the document text
     * @param anchorPosition selection anchor offset in the document text
     * @param horizontalScroll estimated horizontal scroll offset
     * @param verticalScroll estimated vertical scroll offset
     * @param folds folded paragraph ranges to restore
     */
    public EditorViewState {
        caretPosition = Math.max(0, caretPosition);
        anchorPosition = Math.max(0, anchorPosition);
        horizontalScroll = validScroll(horizontalScroll);
        verticalScroll = validScroll(verticalScroll);
        folds = folds == null
            ? List.of()
            : folds.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Creates view state with nonnegative positions, valid scroll offsets, and an immutable fold list.
     *
     * @param caretPosition caret offset in the document text
     * @param anchorPosition selection anchor offset in the document text
     */
    public EditorViewState(int caretPosition, int anchorPosition) {
        this(caretPosition, anchorPosition, 0.0, 0.0, List.of());
    }

    /**
     * Captures the current selection, estimated scroll offsets, and folded paragraphs.
     *
     * @param editor text editor to capture, or null for an empty view state
     * @return captured view state, or EMPTY when the editor is null
     */
    public static EditorViewState capture(@Nullable TextEditorPane editor) {
        if (editor == null)
            return EMPTY;

        return new EditorViewState(
            editor.getCaretPosition(),
            editor.getAnchor(),
            editor.estimatedScrollXProperty().getValue(),
            editor.estimatedScrollYProperty().getValue(),
            captureFolds(editor));
    }

    private static List<FoldRange> captureFolds(TextEditorPane editor) {
        var folds = new ArrayList<FoldRange>();
        int paragraphCount = editor.getParagraphs().size();
        int paragraph = 0;
        while (paragraph < paragraphCount) {
            if (!editor.isFolded(paragraph)) {
                paragraph++;
                continue;
            }

            int firstFoldedParagraph = paragraph;
            while (paragraph + 1 < paragraphCount && editor.isFolded(paragraph + 1)) {
                paragraph++;
            }
            folds.add(new FoldRange(Math.max(0, firstFoldedParagraph - 1), paragraph));
            paragraph++;
        }
        return List.copyOf(folds);
    }

    private static double validScroll(double value) {
        return Double.isFinite(value) && value >= 0.0 ? value : 0.0;
    }

    /**
     * Identifies the header and final paragraph of a folded section.
     *
     * @param startParagraph zero-based index of the fold header paragraph
     * @param endParagraph zero-based index of the final folded paragraph
     */
    public record FoldRange(int startParagraph, int endParagraph) {
        /**
         * Creates a fold range with nonnegative, ordered paragraph indexes.
         *
         * @param startParagraph zero-based index of the fold header paragraph
         * @param endParagraph zero-based index of the final folded paragraph
         */
        public FoldRange {
            startParagraph = Math.max(0, startParagraph);
            endParagraph = Math.max(startParagraph, endParagraph);
        }
    }
}
