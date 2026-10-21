package dev.railroadide.railroad.ui;

import dev.railroadide.railroad.ui.localized.LocalizedTextProperty;
import javafx.scene.control.TextArea;

/**
 * A modern text area component with consistent styling and localization support.
 */
public class RRTextArea extends TextArea {
    /**
     * CSS classes installed when the text area is initialized.
     */
    public static final String[] DEFAULT_STYLE_CLASSES = {"rr-text-area", "text-area"};

    private final LocalizedTextProperty localizedPromptText = new LocalizedTextProperty(this, "localizedPromptText",
        null);

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

    /**
     * Installs Railroad style classes and binds the localized prompt property.
     */
    protected void initialize() {
        getStyleClass().setAll(DEFAULT_STYLE_CLASSES);
        promptTextProperty().bindBidirectional(localizedPromptText);
    }

    /**
     * Sets prompt text that follows the application's selected language.
     *
     * @param localizationKey the translation key for the prompt
     * @param args formatting arguments for the translation
     */
    public void setLocalizedPlaceholder(String localizationKey, Object... args) {
        localizedPromptText.setTranslation(localizationKey, args);
    }
}
