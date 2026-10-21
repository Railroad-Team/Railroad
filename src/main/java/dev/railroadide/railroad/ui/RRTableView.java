package dev.railroadide.railroad.ui;

import javafx.collections.ObservableList;
import javafx.scene.control.TableView;

/**
 * Table view with Railroad CSS classes and optional bordered or striped styling.
 *
 * @param <T> the type of each table row
 */
public class RRTableView<T> extends TableView<T> {

    /**
     * CSS classes installed when the table is initialized.
     */
    public static final String[] DEFAULT_STYLE_CLASSES = {"rr-table-view", "table-view"};

    /**
     * Creates an empty table with Railroad styling.
     */
    public RRTableView() {
        super();
        initialize();
    }

    /**
     * Creates a styled table backed by the supplied observable items.
     *
     * @param items the observable list of row values
     */
    public RRTableView(ObservableList<T> items) {
        super(items);
        initialize();
    }

    /**
     * Replaces the table's style classes with the Railroad defaults.
     */
    protected void initialize() {
        getStyleClass().setAll(DEFAULT_STYLE_CLASSES);
    }

    /**
     * Adds or removes styling for a border.
     *
     * @param bordered true to enable bordered styling
     */
    public void setBordered(boolean bordered) {
        if (bordered) {
            getStyleClass().add("bordered");
        } else {
            getStyleClass().remove("bordered");
        }
    }

    /**
     * Adds or removes styling for alternating row backgrounds.
     *
     * @param striped true to enable striped styling
     */
    public void setStriped(boolean striped) {
        if (striped) {
            getStyleClass().add("striped");
        } else {
            getStyleClass().remove("striped");
        }
    }
}
