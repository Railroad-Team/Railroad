package dev.railroadide.railroad.ui;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import org.jetbrains.annotations.Nullable;

/**
 * A vertically arranged card with rounded clipping and an elevation style that increases on hover.
 * Content is held in an inner container that is hidden and unmanaged while empty.
 */
public class RRCard extends VBox {
    private final VBox content;

    /**
     * Creates an empty card with a clipping arc size of eight pixels and default content padding.
     */
    public RRCard() {
        this(8);
    }

    /**
     * Creates an empty card with the specified rounded clipping and default content padding.
     *
     * @param cornerRadius width and height of the clipping rectangle's corner arcs, in pixels
     */
    public RRCard(double cornerRadius) {
        this(cornerRadius, null);
    }

    /**
     * Creates an empty card with the specified rounded clipping and content padding.
     *
     * @param cornerRadius width and height of the clipping rectangle's corner arcs, in pixels
     * @param padding padding for the inner content container, or {@code null} to retain its default
     */
    public RRCard(double cornerRadius, @Nullable Insets padding) {
        content = new VBox();
        content.getStyleClass().add("rr-card-content");
        if (padding != null) {
            content.setPadding(padding);
        }
        content.visibleProperty().bind(Bindings.isNotEmpty(content.getChildren()));
        content.managedProperty().bind(Bindings.isNotEmpty(content.getChildren()));

        // Create a clip rectangle for the RRCard itself
        var clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        clip.setArcWidth(cornerRadius);
        clip.setArcHeight(cornerRadius);
        setClip(clip);

        getChildren().add(content);
        getStyleClass().addAll("rr-card", "elevated-1");

        setOnMouseEntered(_ -> {
            getStyleClass().remove("elevated-1");
            getStyleClass().add("elevated-2");
        });

        setOnMouseExited(_ -> {
            getStyleClass().remove("elevated-2");
            getStyleClass().add("elevated-1");
        });
    }

    /**
     * Appends nodes to the card's inner vertical content container.
     *
     * @param nodes nodes to append in layout order
     */
    public void addContent(Node... nodes) {
        content.getChildren().addAll(nodes);
    }

    /**
     * Removes all content, causing the empty inner container to become hidden and unmanaged.
     */
    public void clearContent() {
        content.getChildren().clear();
    }

    /**
     * Toggles the interactive CSS style without installing a click handler.
     *
     * @param interactive whether to apply the interactive style
     */
    public void setInteractive(boolean interactive) {
        if (interactive) {
            getStyleClass().add("interactive");
        } else {
            getStyleClass().remove("interactive");
        }
    }

    /**
     * Toggles the highlighted CSS style.
     *
     * @param highlighted whether to apply the highlighted style
     */
    public void setHighlighted(boolean highlighted) {
        if (highlighted) {
            getStyleClass().add("highlighted");
        } else {
            getStyleClass().remove("highlighted");
        }
    }

    /**
     * Toggles the selected CSS style.
     *
     * @param selected whether to apply the selected style
     */
    public void setSelected(boolean selected) {
        if (selected) {
            getStyleClass().add("selected");
        } else {
            getStyleClass().remove("selected");
        }
    }
}
