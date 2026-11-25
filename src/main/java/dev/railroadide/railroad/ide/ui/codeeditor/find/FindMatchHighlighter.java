package dev.railroadide.railroad.ide.ui.codeeditor.find;

import dev.railroadide.railroad.ide.sst.document.api.TextRange;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import org.fxmisc.richtext.model.TwoDimensional;
import org.reactfx.Subscription;

import java.util.List;
import java.util.Objects;

/**
 * Draws find matches over the visible part of a text editor without changing its text styles.
 * Only matches in visible paragraphs are drawn, so the cost does not grow with the document.
 */
public final class FindMatchHighlighter {
    /**
     * Style class of the shapes drawn over matches other than the current one.
     */
    public static final String MATCH_STYLE_CLASS = "find-match";
    /**
     * Style class of the shapes drawn over the current match.
     */
    public static final String CURRENT_MATCH_STYLE_CLASS = "find-match-current";
    private static final int MAX_DRAWN_SEGMENTS = 2_000;

    private final TextEditorPane editor;
    private final Pane layer = new Pane();
    private final Rectangle clip = new Rectangle();
    private final Runnable redrawAfterLayout = this::redrawIfDirty;
    private final ChangeListener<Scene> sceneListener = (_, previous, current) -> moveLayoutListener(previous,
        current);
    private List<TextRange> matches = List.of();
    private TextRange current;
    private Subscription viewportSubscription;
    private boolean dirty;

    /**
     * Creates a highlighter that draws nothing until matches are shown.
     *
     * @param editor editor to draw over
     */
    public FindMatchHighlighter(TextEditorPane editor) {
        this.editor = Objects.requireNonNull(editor, "editor");
        layer.getStyleClass().add("find-match-layer");
        layer.setManaged(false);
        layer.setMouseTransparent(true);
        layer.setClip(clip);
        clip.widthProperty().bind(editor.widthProperty());
        clip.heightProperty().bind(editor.heightProperty());
    }

    /**
     * Shows matches in document order, emphasizing the current one.
     *
     * @param matches non-overlapping matches sorted by offset
     * @param current current match, or {@code null}
     */
    public void show(List<TextRange> matches, TextRange current) {
        this.matches = List.copyOf(matches);
        this.current = current;
        if (viewportSubscription == null) {
            editor.addFoldOverlay(layer);
            viewportSubscription = editor.viewportDirtyEvents().subscribe(_ -> markDirty());
            editor.sceneProperty().addListener(sceneListener);
            moveLayoutListener(null, editor.getScene());
        }

        markDirty();
    }

    /**
     * Removes every highlight and stops following the editor's viewport.
     */
    public void clear() {
        matches = List.of();
        current = null;
        layer.getChildren().clear();
        if (viewportSubscription == null)
            return;

        viewportSubscription.unsubscribe();
        viewportSubscription = null;
        editor.sceneProperty().removeListener(sceneListener);
        moveLayoutListener(editor.getScene(), null);
        editor.removeFoldOverlay(layer);
    }

    /**
     * Returns how many shapes the last redraw produced; a match spanning lines has one per line.
     *
     * @return number of drawn match segments
     */
    public int drawnSegmentCount() {
        return layer.getChildren().size();
    }

    private void moveLayoutListener(Scene previous, Scene next) {
        if (previous != null) {
            previous.removePostLayoutPulseListener(redrawAfterLayout);
        }

        if (next != null) {
            next.addPostLayoutPulseListener(redrawAfterLayout);
            markDirty();
        }
    }

    private void markDirty() {
        dirty = true;
        if (editor.getScene() != null) {
            Platform.requestNextPulse();
        }
    }

    private void redrawIfDirty() {
        if (!dirty)
            return;

        dirty = false;
        redraw();
    }

    private void redraw() {
        layer.getChildren().clear();
        int visibleParagraphs = editor.getVisibleParagraphs().size();
        if (matches.isEmpty() || visibleParagraphs == 0 || editor.getScene() == null)
            return;

        int firstParagraph = editor.visibleParToAllParIndex(0);
        int lastParagraph = editor.visibleParToAllParIndex(visibleParagraphs - 1);
        int visibleStart = editor.getAbsolutePosition(firstParagraph, 0);
        int visibleEnd = editor.getAbsolutePosition(lastParagraph, editor.getParagraphLength(lastParagraph));
        for (int index = firstMatchEndingAfter(visibleStart); index < matches.size(); index++) {
            TextRange match = matches.get(index);
            if (match.start > visibleEnd || layer.getChildren().size() >= MAX_DRAWN_SEGMENTS)
                break;

            drawMatch(match, Objects.equals(match, current) ? CURRENT_MATCH_STYLE_CLASS : MATCH_STYLE_CLASS);
        }
    }

    private int firstMatchEndingAfter(int offset) {
        int low = 0;
        int high = matches.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (matches.get(middle).end <= offset) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }

        return low;
    }

    private void drawMatch(TextRange match, String styleClass) {
        int end = Math.min(match.end, editor.getLength());
        int firstParagraph = editor.offsetToPosition(match.start, TwoDimensional.Bias.Forward).getMajor();
        int lastParagraph = editor.offsetToPosition(end, TwoDimensional.Bias.Backward).getMajor();
        for (int paragraph = firstParagraph; paragraph <= lastParagraph; paragraph++) {
            int paragraphStart = editor.getAbsolutePosition(paragraph, 0);
            int segmentStart = Math.max(match.start, paragraphStart);
            int segmentEnd = Math.min(end, paragraphStart + editor.getParagraphLength(paragraph));
            if (segmentEnd <= segmentStart)
                continue;

            editor.getCharacterBoundsOnScreen(segmentStart, segmentEnd)
                .map(editor::screenToLocal)
                .filter(bounds -> bounds.getWidth() > 0 && bounds.getHeight() > 0)
                .ifPresent(bounds -> layer.getChildren().add(segment(bounds, styleClass)));
        }
    }

    private static Rectangle segment(Bounds bounds, String styleClass) {
        var rectangle = new Rectangle(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
        rectangle.getStyleClass().add(styleClass);
        return rectangle;
    }
}
