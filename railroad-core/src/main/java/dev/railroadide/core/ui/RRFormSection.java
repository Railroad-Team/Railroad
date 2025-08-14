package dev.railroadide.core.ui;

import dev.railroadide.core.ui.localized.LocalizedLabel;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/**
 * A modern form section component that provides a clean, elevated container for form fields.
 * Perfect for grouping related form elements with a consistent modern design.
 */
public class RRFormSection extends VBox {
    private final LocalizedLabel headerLabel;
    @Getter
    private final VBox contentArea;

    public RRFormSection() {
        this(null);
    }

    public RRFormSection(@Nullable String headerText) {
        this(headerText, new Insets(20));
    }

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

        contentArea = new VBox(12);
        contentArea.getStyleClass().add("section-content");

        setPadding(padding);
        setSpacing(16);

        getChildren().addAll(headerLabel, contentArea);
        VBox.setVgrow(contentArea, javafx.scene.layout.Priority.ALWAYS);
    }

    /**
     * Set the section header text (non-localized)
     */
    public void setHeaderText(String headerText) {
        headerLabel.setText(headerText);
        headerLabel.setVisible(headerText != null && !headerText.trim().isEmpty());
    }

    /**
     * Set the section header text using a localization key
     */
    public void setLocalizedHeaderText(String localizationKey, Object... args) {
        headerLabel.setKey(localizationKey, args);
        headerLabel.setVisible(localizationKey != null && !localizationKey.trim().isEmpty());
    }

    /**
     * Get the section header text
     */
    public String getHeaderText() {
        return headerLabel.getText();
    }

    /**
     * Get the localization key if the header is localized
     */
    public String getLocalizationKey() {
        return headerLabel.getKey();
    }

    /**
     * Add content to the form section
     */
    public void addContent(Node... nodes) {
        contentArea.getChildren().addAll(nodes);
    }

    /**
     * Add content to the form section at a specific index
     */
    public void addContent(int index, Node... nodes) {
        contentArea.getChildren().addAll(index, java.util.Arrays.asList(nodes));
    }

    /**
     * Remove content from the form section
     */
    public void removeContent(Node... nodes) {
        contentArea.getChildren().removeAll(nodes);
    }

    /**
     * Clear all content from the form section
     */
    public void clearContent() {
        contentArea.getChildren().clear();
    }

    /**
     * Set the section as compact (reduced padding and spacing)
     */
    public void setCompact(boolean compact) {
        if (compact) {
            getStyleClass().add("compact");
            setPadding(new Insets(12));
            setSpacing(8);
            contentArea.setSpacing(8);
        } else {
            getStyleClass().remove("compact");
            setPadding(new Insets(20));
            setSpacing(16);
            contentArea.setSpacing(12);
        }
    }

    /**
     * Set the section as highlighted
     */
    public void setHighlighted(boolean highlighted) {
        if (highlighted) {
            getStyleClass().add("highlighted");
        } else {
            getStyleClass().remove("highlighted");
        }
    }

    /**
     * Set the section as interactive (clickable)
     */
    public void setInteractive(boolean interactive) {
        if (interactive) {
            getStyleClass().add("interactive");
        } else {
            getStyleClass().remove("interactive");
        }
    }
} 