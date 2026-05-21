package dev.railroadide.railroad.utility.javafx;

import javafx.geometry.Bounds;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class JavaFXUtils {
    private static final Map<MeasurementKey, Double> TEXT_WIDTH_CACHE = new ConcurrentHashMap<>();

    private JavaFXUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

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

    private record MeasurementKey(String text, String fontFamily, double fontSize, String fontStyle) {
    }
}
