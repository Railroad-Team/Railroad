package dev.railroadide.railroad.ide.ui.codeeditor;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * Owns an editor's paragraph graphics, combining line numbers with independent marker columns.
 * Columns appear in registration order after the line number. All public line callbacks use
 * one-based line numbers. Construct and update the gutter on the JavaFX application thread.
 */
public final class EditorGutter {
    private final CodeArea editor;
    private final Map<String, GutterColumn> columns = new LinkedHashMap<>();
    private IntFunction<Node> lineNumberFactory;

    /**
     * Installs a gutter with standard RichTextFX line numbers.
     *
     * @param editor editor whose paragraph graphics this gutter owns
     */
    public EditorGutter(CodeArea editor) {
        this.editor = Objects.requireNonNull(editor, "editor");
        IntFunction<Node> defaultFactory = LineNumberFactory.get(editor);
        lineNumberFactory = line -> defaultFactory.apply(line - 1);
        refresh();
    }

    /**
     * Registers or replaces a column, retaining its position when replacing an existing id.
     *
     * @param column marker column to display
     */
    public void addColumn(GutterColumn column) {
        Objects.requireNonNull(column, "column");
        columns.put(column.id(), column);
        refresh();
    }

    /**
     * Removes a registered column and refreshes the gutter if it was present.
     *
     * @param id identifier of the column to remove
     */
    public void removeColumn(String id) {
        if (columns.remove(id) != null) {
            refresh();
        }
    }

    /**
     * Customizes line number rendering without replacing the marker columns.
     *
     * @param factory factory receiving one-based line numbers and returning a new node each time
     */
    public void setLineNumberFactory(IntFunction<Node> factory) {
        lineNumberFactory = Objects.requireNonNull(factory, "factory");
        refresh();
    }

    /**
     * Rebuilds paragraph graphics so visible markers reflect their providers' current state.
     * Off-screen paragraphs use that state when RichTextFX renders them.
     */
    public void refresh() {
        // A new factory instance invalidates RichTextFX's cached paragraph graphics.
        editor.setParagraphGraphicFactory(new IntFunction<>() {
            @Override
            public Node apply(int paragraphIndex) {
                return createGraphic(paragraphIndex + 1);
            }
        });
    }

    private Node createGraphic(int line) {
        Node number = lineNumberFactory.apply(line);
        if (columns.isEmpty())
            return number;

        var grid = new GridPane();
        grid.setHgap(5);
        grid.getStyleClass().add("ide-code-editor-grid");

        var numberColumn = new ColumnConstraints();
        numberColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().add(numberColumn);
        grid.add(number, 0, 0);

        int columnIndex = 1;
        for (GutterColumn column : columns.values()) {
            var constraints = new ColumnConstraints(column.width(), column.width(), column.width());
            constraints.setHgrow(Priority.NEVER);
            grid.getColumnConstraints().add(constraints);

            var cell = new StackPane();
            cell.setAlignment(Pos.CENTER_LEFT);
            cell.setMinWidth(column.width());
            cell.setPrefWidth(column.width());
            cell.setMaxWidth(column.width());
            cell.setPickOnBounds(true);
            cell.getStyleClass().add("editor-gutter-cell");
            Node marker = column.markerFactory().apply(line);
            if (marker != null) {
                cell.getChildren().add(marker);
            }
            if (column.onClick() != null) {
                cell.setOnMouseClicked(event -> column.onClick().accept(line, event));
            }
            grid.add(cell, columnIndex++, 0);
        }

        return grid;
    }
}
