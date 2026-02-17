package dev.railroadide.core.ui;

import dev.railroadide.core.ui.localized.LocalizedTextProperty;
import javafx.scene.control.TextArea;

/**
 * A modern text area component with consistent styling and localization support.
 */
public class RRTextArea extends TextArea {
    public static final String[] DEFAULT_STYLE_CLASSES = { "rr-text-area", "text-area" };

    private final LocalizedTextProperty localizedPromptText = new LocalizedTextProperty(this, "localizedPromptText", null);

    /**
     * Constructs a new text area with empty text and default styling.
     */
    public RRTextArea() {
        this((String) null);
    }

    /**
     * Constructs a new text area with localized placeholder text.
     *
     * @param localizationKey the localization key for the placeholder text
     * @param args optional formatting arguments for the localized text
     */
    public RRTextArea(String localizationKey, Object... args) {
        super();
        initialize();

        if (localizationKey != null) {
            setLocalizedPlaceholder(localizationKey, args);
        }
    }

    protected void initialize() {
        getStyleClass().setAll(DEFAULT_STYLE_CLASSES);
        promptTextProperty().bindBidirectional(localizedPromptText);
    }

    /**
     * Set the placeholder text using a localization key.
     */
    public void setLocalizedPlaceholder(String localizationKey, Object... args) {
        localizedPromptText.setTranslation(localizationKey, args);
    }
}
