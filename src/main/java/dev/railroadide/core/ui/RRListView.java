package dev.railroadide.core.ui;

import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.util.Duration;

/**
 * A modern list view component with enhanced styling and smooth animations.
 * Provides better visual feedback and modern design patterns.
 */
public class RRListView<T> extends ListView<T> {
    private ListViewSize size = ListViewSize.MEDIUM;
    private boolean enableAnimations = true;
    private TranslateTransition selectionTransition;

    /**
     * Constructs an empty modern list view with default styling and animations.
     */
    public RRListView() {
        super();
        initialize();
    }

    /**
     * Constructs a modern list view with the specified items and default styling.
     * 
     * @param items the observable list of items to display
     */
    public RRListView(ObservableList<T> items) {
        super(items);
        initialize();
    }

    /**
     * Create a modern list view with items
     * 
     * @param items the observable list of items to display
     * @param <T> the type of items in the list
     * @return a new RRListView instance
     */
    public static <T> RRListView<T> create(ObservableList<T> items) {
        return new RRListView<>(items);
    }

    /**
     * Create an empty modern list view
     * 
     * @param <T> the type of items in the list
     * @return a new empty RRListView instance
     */
    public static <T> RRListView<T> create() {
        return new RRListView<>();
    }

    private void initialize() {
        getStyleClass().addAll("rr-list-view", "list-view");
        setPadding(new Insets(8));

        // Enable smooth scrolling
        setFixedCellSize(-1);

        // Add selection animation
        getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (enableAnimations && newVal != null) {
                animateSelection();
            }
        });

        updateStyle();
    }

    /**
     * Set the list view size
     */
    public void setListViewSize(ListViewSize size) {
        this.size = size;
        updateStyle();
    }

    /**
     * Enable or disable animations
     */
    public void setAnimationsEnabled(boolean enabled) {
        this.enableAnimations = enabled;
    }

    /**
     * Set the list view as bordered
     */
    public void setBordered(boolean bordered) {
        if (bordered) {
            getStyleClass().add("bordered");
        } else {
            getStyleClass().remove("bordered");
        }
    }

    /**
     * Set the list view as striped
     */
    public void setStriped(boolean striped) {
        if (striped) {
            getStyleClass().add("striped");
        } else {
            getStyleClass().remove("striped");
        }
    }

    /**
     * Set the list view as dense
     */
    public void setDense(boolean dense) {
        if (dense) {
            getStyleClass().add("dense");
        } else {
            getStyleClass().remove("dense");
        }
    }

    /**
     * Set the list view as edge-to-edge
     */
    public void setEdgeToEdge(boolean edgeToEdge) {
        if (edgeToEdge) {
            getStyleClass().add("edge-to-edge");
        } else {
            getStyleClass().remove("edge-to-edge");
        }
    }

    /**
     * Add item with animation
     */
    public void addItemWithAnimation(T item) {
        if (enableAnimations) {
            var fade = new FadeTransition(Duration.millis(300), this);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);
            fade.play();
        }

        getItems().add(item);
    }

    /**
     * Remove item with animation
     */
    public void removeItemWithAnimation(T item) {
        if (enableAnimations) {
            var fade = new FadeTransition(Duration.millis(200), this);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setOnFinished(e -> getItems().remove(item));
            fade.play();
        } else {
            getItems().remove(item);
        }
    }

    /**
     * Clear all items with animation
     */
    public void clearWithAnimation() {
        if (enableAnimations) {
            var fade = new FadeTransition(Duration.millis(300), this);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setOnFinished(e -> {
                getItems().clear();
                var fadeBack = new FadeTransition(Duration.millis(300), this);
                fadeBack.setFromValue(0.0);
                fadeBack.setToValue(1.0);
                fadeBack.play();
            });

            fade.play();
        } else {
            getItems().clear();
        }
    }

    /**
     * Set multiple selection mode
     */
    public void setMultipleSelection(boolean multiple) {
        if (multiple) {
            getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        } else {
            getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        }
    }

    private void animateSelection() {
        if (!enableAnimations)
            return;

        // Stop any previous animation and reset translation
        if (selectionTransition != null) {
            selectionTransition.stop();
            setTranslateX(0);
        }

        selectionTransition = new TranslateTransition(Duration.millis(150), this);
        selectionTransition.setByX(5);
        selectionTransition.setAutoReverse(true);
        selectionTransition.setCycleCount(2);
        selectionTransition.setOnFinished(e -> setTranslateX(0));
        selectionTransition.play();
    }

    private void updateStyle() {
        getStyleClass().removeAll("small", "medium", "large");

        switch (size) {
            case SMALL -> getStyleClass().add("small");
            case MEDIUM -> getStyleClass().add("medium");
            case LARGE -> getStyleClass().add("large");
        }
    }

    public enum ListViewSize {
        SMALL, MEDIUM, LARGE
    }
} 