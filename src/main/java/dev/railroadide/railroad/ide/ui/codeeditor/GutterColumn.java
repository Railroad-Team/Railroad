package dev.railroadide.railroad.ide.ui.codeeditor;

import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;

/**
 * A fixed-width marker column in an editor gutter. All callbacks receive one-based line numbers.
 * The marker factory must return a new node for each invocation, or null for an empty cell.
 * Click handlers also run for empty cells and may consume the event to prevent editor handling.
 * Call {@link EditorGutter#refresh()} after changing the state read by the marker factory.
 *
 * @param id unique column identifier within the gutter
 * @param width width reserved for the column, in pixels
 * @param markerFactory factory for each line's marker, including any tooltip
 * @param onClick optional handler for clicks anywhere in a column cell
 */
public record GutterColumn(
    String id,
    double width,
    IntFunction<@Nullable Node> markerFactory,
    @Nullable BiConsumer<Integer, MouseEvent> onClick
) {
    /**
     * Validates a gutter column identifier, width and marker factory.
     *
     * @param id unique column identifier within the editor gutter
     * @param width finite positive column width in pixels
     * @param markerFactory factory receiving one-based lines and returning a fresh marker node or null
     * @param onClick optional handler for clicks on filled or empty column cells
     * @throws IllegalArgumentException if the identifier is blank or the width is not finite and positive
     * @throws NullPointerException if the identifier or marker factory is null
     */
    public GutterColumn {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(markerFactory, "markerFactory");
        if (id.isBlank())
            throw new IllegalArgumentException("Column id must not be blank");
        if (!Double.isFinite(width) || width <= 0)
            throw new IllegalArgumentException("Column width must be finite and positive");
    }

    /**
     * Creates a marker column without a click handler.
     *
     * @param id unique column identifier within the gutter
     * @param width width reserved for the column, in pixels
     * @param markerFactory factory receiving one-based line numbers
     */
    public GutterColumn(String id, double width, IntFunction<@Nullable Node> markerFactory) {
        this(id, width, markerFactory, null);
    }
}
