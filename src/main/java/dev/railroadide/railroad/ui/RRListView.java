package dev.railroadide.railroad.ui;

import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;

/**
 * A modern list view component with enhanced styling and smooth animations.
 * Provides better visual feedback and modern design patterns.
 *
 * @param <T> the type of each list item
 */
public class RRListView<T> extends ListView<T> {
    private ListViewSize size = ListViewSize.MEDIUM;
    private boolean enableAnimations = true;
    private TranslateTransition selectionTransition;
    private boolean smoothScrollingEnabled = false;

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
        if (!getStyleClass().contains("rr-list-view")) {
            getStyleClass().add("rr-list-view");
        }

        // Add selection animation
        getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (enableAnimations && newVal != null) {
                animateSelection();
            }
        });

        updateStyle();
    }

    /**
     * Sets the CSS size variant.
     *
     * @param size the list size to apply; must not be null
     */
    public void setListViewSize(ListViewSize size) {
        this.size = size;
        updateStyle();
    }

    /**
     * Controls animations used by selection changes and animated item operations.
     *
     * @param enabled true to animate subsequent operations
     */
    public void setAnimationsEnabled(boolean enabled) {
        this.enableAnimations = enabled;
    }

    /**
     * Adds or removes border styling.
     *
     * @param bordered true to enable a border
     */
    public void setBordered(boolean bordered) {
        if (bordered) {
            getStyleClass().add("bordered");
        } else {
            getStyleClass().remove("bordered");
        }
    }

    /**
     * Adds or removes alternating-row styling.
     *
     * @param striped true to enable striped rows
     */
    public void setStriped(boolean striped) {
        if (striped) {
            getStyleClass().add("striped");
        } else {
            getStyleClass().remove("striped");
        }
    }

    /**
     * Adds or removes compact row styling.
     *
     * @param dense true to enable dense spacing
     */
    public void setDense(boolean dense) {
        if (dense) {
            getStyleClass().add("dense");
        } else {
            getStyleClass().remove("dense");
        }
    }

    /**
     * Adds or removes edge-to-edge list styling.
     *
     * @param edgeToEdge true to enable edge-to-edge styling
     */
    public void setEdgeToEdge(boolean edgeToEdge) {
        if (edgeToEdge) {
            getStyleClass().add("edge-to-edge");
        } else {
            getStyleClass().remove("edge-to-edge");
        }
    }

    /**
     * Appends an item immediately and fades in the whole list when animations are enabled.
     *
     * @param item the value to append
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
     * Removes the first matching item after fading out the whole list, or immediately if animations are disabled.
     *
     * @param item the value to remove
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
     * Clears items after fading out the list, then fades it back in; clears immediately if animations are disabled.
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
     * Switches the selection model between single and multiple selection.
     *
     * @param multiple true to allow multiple selected items
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

    /**
     * CSS size variants for list row presentation.
     */
    public enum ListViewSize {
        /** Compact list sizing. */
        SMALL,
        /** Default list sizing. */
        MEDIUM,
        /** Large list sizing. */
        LARGE
    }

    /**
     * Enables pixel-based vertical scrolling when a subsequently installed skin exposes a virtual flow.
     * Disables fixed cell sizing; repeated calls have no effect.
     */
    public void enableSmoothScrolling() {
        if (smoothScrollingEnabled)
            return;

        smoothScrollingEnabled = true;
        setFixedCellSize(-1);

        skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin == null)
                return;

            // Defer lookup until skin is fully applied.
            Platform.runLater(() -> {
                Node flowNode = lookup(".virtual-flow");
                if (!(flowNode instanceof VirtualFlow<?> virtualFlow))
                    return;

                addEventFilter(ScrollEvent.SCROLL, event -> {
                    if (event.isConsumed() || event.getDeltaY() == 0)
                        return;

                    virtualFlow.scrollPixels(-event.getDeltaY());
                    event.consume();
                });
            });
        });
    }
}
