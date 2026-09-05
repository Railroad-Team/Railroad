package dev.railroadide.railroad.ui;

import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import java.util.Arrays;
import javafx.scene.layout.Priority;

/**
 * A styled vertical group of form fields with an optional localized header and a growing content area.
 */
public class RRFormSection extends VBox {
    private final LocalizedLabel headerLabel;
    /**
     * The inner vertical container for the section's fields.
     *
     * @return the mutable section content container
     */
    @Getter
    private final VBox contentArea;

    /**
     * Creates an empty form section without a visible header and with default padding.
     */
    public RRFormSection() {
        this(null);
    }

    /**
     * Creates an empty form section with a header and default padding.
     *
     * @param headerText header text, interpreted as a localization key when it contains a period;
     *                   {@code null} or blank text hides the header
     */
    public RRFormSection(@Nullable String headerText) {
        this(headerText, null);
    }

    /**
     * Creates an empty form section with a header and optional custom padding.
     *
     * @param headerText header text, interpreted as a localization key when it contains a period;
     *                   {@code null} or blank text hides the header
     * @param padding section padding, or {@code null} to use stylesheet defaults
     */
    public RRFormSection(@Nullable String headerText, Insets padding) {
        getStyleClass().addAll("rr-form-section", "rr-card");

        headerLabel = new LocalizedLabel("");
        headerLabel.getStyleClass().add("section-header");

        if (headerText != null && !headerText.trim().isEmpty()) {
            if (headerText.contains(".")) {
                headerLabel.setKey(headerText);
            } else {
                headerLabel.setText(headerText);
            }
        }

        headerLabel.setVisible(headerText != null && !headerText.trim().isEmpty());

        contentArea = new VBox();
        contentArea.getStyleClass().add("section-content");

        if (padding != null) {
            setPadding(padding);
            getStyleClass().add("rr-form-section-custom-padding");
        }

        getChildren().addAll(headerLabel, contentArea);
        VBox.setVgrow(contentArea, Priority.ALWAYS);
    }

    /**
     * Sets the displayed header text directly and updates its visibility.
     *
     * @param headerText header text, or {@code null} or blank text to hide the header
     */
    public void setHeaderText(String headerText) {
        headerLabel.setText(headerText);
        headerLabel.setVisible(headerText != null && !headerText.trim().isEmpty());
    }

    /**
     * Sets the section header using a localization key and updates its visibility.
     *
     * @param localizationKey header localization key, or {@code null} or blank text to hide the header
     * @param args formatting arguments for the localized header
     */
    public void setLocalizedHeaderText(String localizationKey, Object... args) {
        headerLabel.setVisible(localizationKey != null && !localizationKey.trim().isEmpty());
        if (localizationKey != null) {
            headerLabel.setKey(localizationKey, args);
        }
    }

    /**
     * Returns the header label's current text.
     *
     * @return current header text, which may be {@code null}
     */
    public String getHeaderText() {
        return headerLabel.getText();
    }

    /**
     * Returns the header label's stored localization key.
     *
     * @return stored localization key; directly setting text does not clear this key
     */
    public String getLocalizationKey() {
        return headerLabel.getKey();
    }

    /**
     * Appends nodes to the section content area.
     *
     * @param nodes nodes to append in layout order
     */
    public void addContent(Node... nodes) {
        contentArea.getChildren().addAll(nodes);
    }

    /**
     * Inserts nodes into the section content area at the specified index.
     *
     * @param index insertion position, from zero through the current number of content nodes
     * @param nodes nodes to insert in layout order
     * @throws IndexOutOfBoundsException if the index is outside the insertion range
     */
    public void addContent(int index, Node... nodes) {
        contentArea.getChildren().addAll(index, Arrays.asList(nodes));
    }

    /**
     * Removes the supplied nodes from the section content area when present.
     *
     * @param nodes nodes to remove
     */
    public void removeContent(Node... nodes) {
        contentArea.getChildren().removeAll(nodes);
    }

    /**
     * Removes every node from the section content area.
     */
    public void clearContent() {
        contentArea.getChildren().clear();
    }

    /**
     * Toggles the compact CSS style for reduced padding and spacing.
     *
     * @param compact whether to apply compact styling
     */
    public void setCompact(boolean compact) {
        if (compact) {
            getStyleClass().add("compact");
        } else {
            getStyleClass().remove("compact");
        }
    }

    /**
     * Toggles the highlighted CSS style.
     *
     * @param highlighted whether to apply highlighted styling
     */
    public void setHighlighted(boolean highlighted) {
        if (highlighted) {
            getStyleClass().add("highlighted");
        } else {
            getStyleClass().remove("highlighted");
        }
    }

    /**
     * Toggles the interactive CSS style without installing a click handler.
     *
     * @param interactive whether to apply interactive styling
     */
    public void setInteractive(boolean interactive) {
        if (interactive) {
            getStyleClass().add("interactive");
        } else {
            getStyleClass().remove("interactive");
        }
    }
}
