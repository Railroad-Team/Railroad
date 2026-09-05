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
 * A styled vertical form container with an optional localized title and a growing content area.
 */
public class RRFormContainer extends VBox {
    private final LocalizedLabel titleLabel;
    /**
     * The inner vertical container for form sections and other content.
     *
     * @return the mutable form content container
     */
    @Getter
    private final VBox formContent;

    /**
     * Creates an empty form container without a visible title and with default padding.
     */
    public RRFormContainer() {
        this(null);
    }

    /**
     * Creates an empty form container with a title and default padding.
     *
     * @param title title text, interpreted as a localization key when it contains a period;
     *            {@code null} or blank text hides the title
     */
    public RRFormContainer(@Nullable String title) {
        this(title, null);
    }

    /**
     * Creates an empty form container with a title and optional custom padding.
     *
     * @param title title text, interpreted as a localization key when it contains a period;
     *            {@code null} or blank text hides the title
     * @param padding container padding, or {@code null} to use stylesheet defaults
     */
    public RRFormContainer(@Nullable String title, Insets padding) {
        getStyleClass().addAll("rr-form-container", "rr-card");

        titleLabel = new LocalizedLabel("");
        titleLabel.getStyleClass().add("form-title");

        if (title != null && title.contains(".")) {
            setLocalizedTitle(title);
        } else {
            setTitle(title);
        }

        formContent = new VBox();
        formContent.getStyleClass().add("form-content");

        initialize(padding);
    }

    /**
     * Applies optional padding, configures content growth, and tracks title visibility for layout.
     *
     * @param padding container padding, or {@code null} to use stylesheet defaults
     */
    protected void initialize(Insets padding) {
        if (padding != null) {
            setPadding(padding);
            getStyleClass().add("rr-form-container-custom-padding");
        }

        VBox.setVgrow(formContent, Priority.ALWAYS);
        updateLayout();

        titleLabel.visibleProperty().addListener(_observable -> updateLayout());
    }

    /**
     * Sets the displayed form title directly and updates its visibility.
     *
     * @param title title text, or {@code null} or blank text to hide the title
     */
    public void setTitle(String title) {
        titleLabel.setText(title);
        titleLabel.setVisible(title != null && !title.trim().isEmpty());
    }

    /**
     * Sets the form title using a localization key and updates its visibility.
     *
     * @param localizationKey title localization key, or {@code null} or blank text to hide the title
     * @param args formatting arguments for the localized title
     */
    public void setLocalizedTitle(@Nullable String localizationKey, Object... args) {
        titleLabel.setVisible(localizationKey != null && !localizationKey.trim().isEmpty());
        if (localizationKey != null) {
            titleLabel.setKey(localizationKey, args);
        }
    }

    private void updateLayout() {
        getChildren().clear();

        if (titleLabel.isVisible()) {
            getChildren().add(titleLabel);
        }
        getChildren().add(formContent);
    }

    /**
     * Returns the title label's current text.
     *
     * @return current title text, which may be {@code null}
     */
    public String getTitle() {
        return titleLabel.getText();
    }

    /**
     * Returns the title label's stored localization key.
     *
     * @return stored localization key; directly setting text does not clear this key
     */
    public String getLocalizationKey() {
        return titleLabel.getKey();
    }

    /**
     * Appends nodes to the form content area.
     *
     * @param nodes nodes to append in layout order
     */
    public void addContent(Node... nodes) {
        formContent.getChildren().addAll(nodes);
    }

    /**
     * Inserts nodes into the form content area at the specified index.
     *
     * @param index insertion position, from zero through the current number of content nodes
     * @param nodes nodes to insert in layout order
     * @throws IndexOutOfBoundsException if the index is outside the insertion range
     */
    public void addContent(int index, Node... nodes) {
        formContent.getChildren().addAll(index, Arrays.asList(nodes));
    }

    /**
     * Removes the supplied nodes from the form content area when present.
     *
     * @param nodes nodes to remove
     */
    public void removeContent(Node... nodes) {
        formContent.getChildren().removeAll(nodes);
    }

    /**
     * Removes every node from the form content area.
     */
    public void clearContent() {
        formContent.getChildren().clear();
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
