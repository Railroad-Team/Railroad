package dev.railroadide.railroad.ide.ui.editor;

import java.util.Objects;

/**
 * Stores a detached editor window layout and its screen bounds.
 *
 * @param layout editor layout contained in the window
 * @param x horizontal screen coordinate of the window
 * @param y vertical screen coordinate of the window
 * @param width window width in screen coordinates
 * @param height window height in screen coordinates
 * @param maximized whether the window is maximized
 */
public record DetachedEditorWindowState(
    EditorLayoutNodeState layout,
    double x,
    double y,
    double width,
    double height,
    boolean maximized
) {

    /**
     * Creates window state, replacing invalid coordinates and sizes with usable defaults.
     *
     * @param layout editor layout contained in the window
     * @param x horizontal screen coordinate of the window
     * @param y vertical screen coordinate of the window
     * @param width window width in screen coordinates
     * @param height window height in screen coordinates
     * @param maximized whether the window is maximized
     */
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
