package dev.railroadide.railroad.ui;

import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import org.jetbrains.annotations.Nullable;

/**
 * A modern card component with enhanced styling and hover effects.
 * Provides a clean, elevated design that's perfect for content containers.
 */
public class RRCard extends VBox {
    private final VBox content;

    public RRCard() {
        this(8);
    }

    public RRCard(double cornerRadius) {
        this(cornerRadius, null);
    }

    public RRCard(double cornerRadius, @Nullable javafx.geometry.Insets padding) {
        content = new VBox();
        content.getStyleClass().add("rr-card-content");
        if (padding != null) {
            content.setPadding(padding);
        }
        content.visibleProperty().bind(Bindings.isNotEmpty(content.getChildren()));
        content.managedProperty().bind(Bindings.isNotEmpty(content.getChildren()));

        // Create a clip rectangle for the RRCard itself
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        clip.setArcWidth(cornerRadius);
        clip.setArcHeight(cornerRadius);
        setClip(clip);

        getChildren().add(content);
        getStyleClass().addAll("rr-card", "elevated-1");

        setOnMouseEntered($ -> {
            getStyleClass().remove("elevated-1");
            getStyleClass().add("elevated-2");
        });

        setOnMouseExited($ -> {
            getStyleClass().remove("elevated-2");
            getStyleClass().add("elevated-1");
        });
    }

    /**
     * Add content to the card
     */
    public void addContent(Node... nodes) {
        content.getChildren().addAll(nodes);
    }

    /**
     * Clear all content from the card
     */
    public void clearContent() {
        content.getChildren().clear();
    }

    /**
     * Set the card as interactive (clickable)
     */
    public void setInteractive(boolean interactive) {
        if (interactive) {
            getStyleClass().add("interactive");
        } else {
            getStyleClass().remove("interactive");
        }
    }

    /**
     * Set the card as highlighted
     */
    public void setHighlighted(boolean highlighted) {
        if (highlighted) {
            getStyleClass().add("highlighted");
        } else {
            getStyleClass().remove("highlighted");
        }
    }

    /**
     * Set the card as selected
     */
    public void setSelected(boolean selected) {
        if (selected) {
            getStyleClass().add("selected");
        } else {
            getStyleClass().remove("selected");
        }
    }
}
