package dev.railroadide.railroad.window;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.List;
import java.util.Objects;

public final class WindowBoundsRestorer {
    private WindowBoundsRestorer() {
    }

    public static void restore(
        Stage stage,
        double x,
        double y,
        double width,
        double height,
        boolean maximized) {
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

    public static Rectangle2D fitToScreens(
        double x,
        double y,
        double width,
        double height,
        List<Rectangle2D> screens,
        Rectangle2D primaryScreen) {
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
