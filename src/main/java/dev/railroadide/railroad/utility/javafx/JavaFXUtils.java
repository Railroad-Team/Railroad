package dev.railroadide.railroad.utility.javafx;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for JavaFX-related operations.
 */
public final class JavaFXUtils {
    private static final Map<MeasurementKey, Double> TEXT_WIDTH_CACHE = new ConcurrentHashMap<>();

    private JavaFXUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Measures the width of the given text when rendered with the specified font.
     * This method caches the results for performance.
     *
     * @param text The text to measure.
     * @param font The font to use for measurement.
     * @return The width of the text in pixels.
     */
    public static double measureTextWidth(String text, Font font) {
        var key = new MeasurementKey(text, font.getFamily(), font.getSize(), font.getStyle());
        Double cached = TEXT_WIDTH_CACHE.get(key);
        if (cached != null)
            return cached;

        var textNode = new Text(text);
        textNode.setFont(font);
        Bounds bounds = textNode.getLayoutBounds();
        double width = bounds.getWidth();
        TEXT_WIDTH_CACHE.put(key, width);
        return width;
    }

    /**
     * Runs the given action on the JavaFX Application Thread.
     * If the current thread is the JavaFX Application Thread, the action is executed immediately.
     * Otherwise, it is scheduled to run later on the JavaFX Application Thread.
     *
     * @param action The action to run.
     */
    public static void runOnApplicationThread(Runnable action) {
        Objects.requireNonNull(action, "Action cannot be null");
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    private record MeasurementKey(String text, String fontFamily, double fontSize, String fontStyle) {
    }
}
