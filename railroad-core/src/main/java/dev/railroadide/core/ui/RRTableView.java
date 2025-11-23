package dev.railroadide.core.ui;

import javafx.collections.ObservableList;
import javafx.scene.control.TableView;

public class RRTableView<T> extends TableView<T> {

    public static final String[] DEFAULT_STYLE_CLASSES = { "rr-table-view", "table-view" };

    public RRTableView() {
        super();
        initialise();
    }

    public RRTableView(ObservableList<T> items) {
        super(items);
        initialise();
    }

    protected void initialise() {
        getStyleClass().setAll(DEFAULT_STYLE_CLASSES);
    }

    /**
     * Set the table view as bordered
     */
    public void setBordered(boolean bordered) {
        if (bordered) {
            getStyleClass().add("bordered");
        } else {
            getStyleClass().remove("bordered");
        }
    }

    /**
     * Set the table view as striped
     */
    public void setStriped(boolean striped) {
        if (striped) {
            getStyleClass().add("striped");
        } else {
            getStyleClass().remove("striped");
        }
    }
}
