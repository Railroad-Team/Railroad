package dev.railroadide.railroad.ide.ui.codeeditor.find;

import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Hosts an editor view beneath a find and replace bar that is created when first used.
 * The open bar floats over the view, except that the view starts below it while the editor is scrolled
 * near the top of the document, so the first lines are never hidden. Focus requests go to the hosted view,
 * so the host can replace it as a tab's content.
 */
public final class EditorFindHost extends Region {
    private static final PseudoClass OVERLAYING = PseudoClass.getPseudoClass("overlaying");

    private final Node content;
    private final InvalidationListener relayout = _ -> requestLayout();
    // A change listener keeps the lazily evaluated scroll offset current, so every scroll is reported.
    private final ChangeListener<Double> scrollListener = (_, _, _) -> requestLayout();
    private TextEditorPane editor;
    private FindReplaceBar bar;

    /**
     * Creates a host for editor content.
     *
     * @param content hosted view
     */
    public EditorFindHost(Node content) {
        this.content = Objects.requireNonNull(content, "content");
        getStyleClass().add("editor-find-host");
        getChildren().add(content);
    }

    /**
     * Finds the host containing a node.
     *
     * @param node node inside a host, or {@code null}
     * @return closest enclosing host, or {@code null}
     */
    public static @Nullable EditorFindHost enclosing(@Nullable Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof EditorFindHost host)
                return host;
        }

        return null;
    }

    /**
     * Sets the editor searched by the bar, such as when a lazy view creates or removes its source editor.
     *
     * @param editor searched editor, or {@code null} when the view has no text source
     */
    public void setEditor(@Nullable TextEditorPane editor) {
        if (this.editor != null) {
            this.editor.estimatedScrollYProperty().removeListener(scrollListener);
        }

        this.editor = editor;
        if (editor != null) {
            editor.estimatedScrollYProperty().addListener(scrollListener);
        }

        if (bar != null) {
            bar.setEditor(editor);
        }

        requestLayout();
    }

    /**
     * Reports whether the hosted view has text to search.
     *
     * @return whether a source editor is present
     */
    public boolean canFind() {
        return editor != null;
    }

    /**
     * Returns the find and replace bar, creating it on first use.
     *
     * @return this host's bar
     */
    public FindReplaceBar bar() {
        if (bar == null) {
            bar = new FindReplaceBar();
            bar.setEditor(editor);
            bar.visibleProperty().addListener(relayout);
            getChildren().add(bar);
        }

        return bar;
    }

    /**
     * Returns how far the hosted view is currently pushed down to keep the start of the document clear of
     * the bar. This is the bar's height minus the editor's scroll offset, and zero while the bar is closed.
     *
     * @return reserved height above the hosted view
     */
    public double reservedHeight() {
        double barHeight = barHeight(barWidth(getWidth() - snappedLeftInset() - snappedRightInset()));
        Double scrolled = editor == null ? null : editor.estimatedScrollYProperty().getValue();
        double scrollY = scrolled == null ? 0 : Math.max(0, scrolled);
        return snapSizeY(Math.max(0, barHeight - scrollY));
    }

    @Override
    public void requestFocus() {
        content.requestFocus();
    }

    @Override
    protected void layoutChildren() {
        double left = snappedLeftInset();
        double top = snappedTopInset();
        double width = getWidth() - left - snappedRightInset();
        double height = getHeight() - top - snappedBottomInset();
        double barWidth = barWidth(width);
        double barHeight = barHeight(barWidth);
        double reserved = Math.min(reservedHeight(), height);
        content.resizeRelocate(left, top + reserved, width, height - reserved);
        if (bar != null && bar.isVisible()) {
            bar.resizeRelocate(left, top, barWidth, barHeight);
            bar.pseudoClassStateChanged(OVERLAYING, reserved < barHeight);
        }
    }

    @Override
    protected double computePrefWidth(double height) {
        return snappedLeftInset() + content.prefWidth(height) + snappedRightInset();
    }

    @Override
    protected double computePrefHeight(double width) {
        return snappedTopInset() + content.prefHeight(width) + snappedBottomInset();
    }

    private double barWidth(double available) {
        return bar == null || !bar.isVisible() ? 0 : snapSizeX(Math.min(bar.prefWidth(-1), available));
    }

    private double barHeight(double width) {
        return bar == null || !bar.isVisible() ? 0 : snapSizeY(bar.prefHeight(width));
    }
}
