package dev.railroadide.railroad.ide.ui.editor;

import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record EditorViewState(
    int caretPosition,
    int anchorPosition,
    double horizontalScroll,
    double verticalScroll,
    List<FoldRange> folds
) {
    public static final EditorViewState EMPTY = new EditorViewState(0, 0, 0.0, 0.0, List.of());

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

    public EditorViewState(int caretPosition, int anchorPosition) {
        this(caretPosition, anchorPosition, 0.0, 0.0, List.of());
    }

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

    public record FoldRange(int startParagraph, int endParagraph) {
        public FoldRange {
            startParagraph = Math.max(0, startParagraph);
            endParagraph = Math.max(startParagraph, endParagraph);
        }
    }
}
