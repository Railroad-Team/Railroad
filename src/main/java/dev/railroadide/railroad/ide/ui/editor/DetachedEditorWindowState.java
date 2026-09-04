package dev.railroadide.railroad.ide.ui.editor;

import java.util.Objects;

public record DetachedEditorWindowState(
    EditorLayoutNodeState layout,
    double x,
    double y,
    double width,
    double height,
    boolean maximized) {

    public DetachedEditorWindowState {
        Objects.requireNonNull(layout, "layout must not be null");
        x = finiteOr(x, 100.0);
        y = finiteOr(y, 100.0);
        width = positiveOr(width, 800.0);
        height = positiveOr(height, 600.0);
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double positiveOr(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }
}
