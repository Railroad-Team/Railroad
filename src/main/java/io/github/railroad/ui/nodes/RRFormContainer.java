package io.github.railroad.ui.nodes;

import io.github.railroad.localization.ui.LocalizedLabel;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/**
 * A modern form container component that provides a clean, elevated container for entire forms.
 * Perfect for wrapping multiple form sections with consistent modern design.
 */
public class RRFormContainer extends VBox {
    private final LocalizedLabel titleLabel;
    @Getter
    private final VBox formContent;

    public RRFormContainer() {
        this(null);
    }

    public RRFormContainer(@Nullable String title) {
        this(title, new Insets(24));
    }

    public RRFormContainer(@Nullable String title, Insets padding) {
        getStyleClass().addAll("rr-form-container", "rr-card");

        titleLabel = new LocalizedLabel("");
        titleLabel.getStyleClass().add("form-title");
        
        if (title != null && !title.trim().isEmpty()) {
            if (title.contains(".")) {
                titleLabel.setKey(title);
            } else {
                titleLabel.setText(title);
            }
        }
        titleLabel.setVisible(title != null && !title.trim().isEmpty());

        formContent = new VBox(20);
        formContent.getStyleClass().add("form-content");

        setPadding(padding);
        setSpacing(20);

        getChildren().addAll(titleLabel, formContent);
        VBox.setVgrow(formContent, javafx.scene.layout.Priority.ALWAYS);
    }

    /**
     * Set the form title (non-localized)
     */
    public void setTitle(String title) {
        titleLabel.setText(title);
        titleLabel.setVisible(title != null && !title.trim().isEmpty());
    }

    /**
     * Set the form title using a localization key
     */
    public void setLocalizedTitle(String localizationKey, Object... args) {
        titleLabel.setKey(localizationKey, args);
        titleLabel.setVisible(localizationKey != null && !localizationKey.trim().isEmpty());
    }

    /**
     * Get the form title
     */
    public String getTitle() {
        return titleLabel.getText();
    }

    /**
     * Get the localization key if the title is localized
     */
    public String getLocalizationKey() {
        return titleLabel.getKey();
    }

    /**
     * Add content to the form container
     */
    public void addContent(Node... nodes) {
        formContent.getChildren().addAll(nodes);
    }

    /**
     * Add content to the form container at a specific index
     */
    public void addContent(int index, Node... nodes) {
        formContent.getChildren().addAll(index, java.util.Arrays.asList(nodes));
    }

    /**
     * Remove content from the form container
     */
    public void removeContent(Node... nodes) {
        formContent.getChildren().removeAll(nodes);
    }

    /**
     * Clear all content from the form container
     */
    public void clearContent() {
        formContent.getChildren().clear();
    }

    /**
     * Set the form as compact (reduced padding and spacing)
     */
    public void setCompact(boolean compact) {
        if (compact) {
            getStyleClass().add("compact");
            setPadding(new Insets(16));
            setSpacing(12);
            formContent.setSpacing(12);
        } else {
            getStyleClass().remove("compact");
            setPadding(new Insets(24));
            setSpacing(20);
            formContent.setSpacing(20);
        }
    }

    /**
     * Set the form as highlighted
     */
    public void setHighlighted(boolean highlighted) {
        if (highlighted) {
            getStyleClass().add("highlighted");
        } else {
            getStyleClass().remove("highlighted");
        }
    }

    /**
     * Set the form as interactive (clickable)
     */
    public void setInteractive(boolean interactive) {
        if (interactive) {
            getStyleClass().add("interactive");
        } else {
            getStyleClass().remove("interactive");
        }
    }
} 