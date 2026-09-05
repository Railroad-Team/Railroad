package dev.railroadide.railroad.window;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.List;
import java.util.Objects;

/**
 * Restores saved window bounds while keeping the window within an available screen's visual bounds.
 */
public final class WindowBoundsRestorer {
    private WindowBoundsRestorer() {
    }

    /**
     * Fits saved bounds to the current screens, applies them to a stage, and restores maximization.
     *
     * @param stage the stage to restore
     * @param x the saved horizontal position
     * @param y the saved vertical position
     * @param width the saved width
     * @param height the saved height
     * @param maximized whether the restored stage should be maximized
     * @throws NullPointerException if stage is null
     */
    public static void restore(
        Stage stage,
        double x,
        double y,
        double width,
        double height,
        boolean maximized
    ) {
        Objects.requireNonNull(stage, "Stage cannot be null");
        List<Rectangle2D> screens = Screen.getScreens().stream()
            .map(Screen::getVisualBounds)
            .toList();
        Rectangle2D bounds = fitToScreens(x, y, width, height, screens, Screen.getPrimary().getVisualBounds());
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        stage.setMaximized(maximized);
    }

    /**
     * Fits saved bounds to the screen containing their center, falling back to the primary screen.
     * Oversized dimensions are reduced to fit. Bounds whose center is off-screen are centered on
     * the primary screen; otherwise their position is clamped to the selected screen.
     * Non-finite positions fall back to the primary screen origin, and non-positive or non-finite
     * dimensions default to at most 800 by 600, limited by the primary screen size.
     *
     * @param x the saved horizontal position
     * @param y the saved vertical position
     * @param width the saved width
     * @param height the saved height
     * @param screens the available visual bounds; null entries are ignored and an empty list uses the primary screen
     * @param primaryScreen the fallback screen's visual bounds
     * @return the restored bounds contained within the selected screen
     * @throws NullPointerException if screens or primaryScreen is null
     */
    public static Rectangle2D fitToScreens(
        double x,
        double y,
        double width,
        double height,
        List<Rectangle2D> screens,
        Rectangle2D primaryScreen
    ) {
        Objects.requireNonNull(screens, "Screens cannot be null");
        primaryScreen = Objects.requireNonNull(primaryScreen, "Primary screen cannot be null");

        List<Rectangle2D> availableScreens = screens.stream()
            .filter(Objects::nonNull)
            .toList();
        if (availableScreens.isEmpty()) {
            availableScreens = List.of(primaryScreen);
        }

        double restoredWidth = positiveOr(width, Math.min(800.0, primaryScreen.getWidth()));
        double restoredHeight = positiveOr(height, Math.min(600.0, primaryScreen.getHeight()));
        double centerX = finiteOr(x, primaryScreen.getMinX()) + restoredWidth / 2.0;
        double centerY = finiteOr(y, primaryScreen.getMinY()) + restoredHeight / 2.0;
        Rectangle2D targetScreen = availableScreens.stream()
            .filter(screen -> screen.contains(centerX, centerY))
            .findFirst()
            .orElse(primaryScreen);

        restoredWidth = Math.min(restoredWidth, targetScreen.getWidth());
        restoredHeight = Math.min(restoredHeight, targetScreen.getHeight());

        boolean originalMonitorAvailable = targetScreen.contains(centerX, centerY);
        double restoredX = originalMonitorAvailable
            ? Math.clamp(finiteOr(x, targetScreen.getMinX()), targetScreen.getMinX(),
                targetScreen.getMaxX() - restoredWidth)
            : targetScreen.getMinX() + (targetScreen.getWidth() - restoredWidth) / 2.0;
        double restoredY = originalMonitorAvailable
            ? Math.clamp(finiteOr(y, targetScreen.getMinY()), targetScreen.getMinY(),
                targetScreen.getMaxY() - restoredHeight)
            : targetScreen.getMinY() + (targetScreen.getHeight() - restoredHeight) / 2.0;
        return new Rectangle2D(restoredX, restoredY, restoredWidth, restoredHeight);
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double positiveOr(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }
}
