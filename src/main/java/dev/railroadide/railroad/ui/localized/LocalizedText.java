package dev.railroadide.railroad.ui.localized;

import javafx.scene.text.Text;

/**
 * An extension of the JavaFX Text that allows for the Text's text to be localised.
 */
public class LocalizedText extends Text {
    private final LocalizedTextProperty localizedText = new LocalizedTextProperty(this, "localizedText", null);

    /**
     * Sets the key and then the set the text to the localized key.
     *
     * @param key The key to be localized.
     */
    public LocalizedText(final String key) {
        super();
        textProperty().bindBidirectional(localizedText);
        setKey(key);
    }

    /**
     * Creates a text node whose translation is formatted with the supplied arguments.
     *
     * @param key the localization key
     * @param args the arguments used to format the translation
     */
    public LocalizedText(final String key, final Object... args) {
        super();
        textProperty().bindBidirectional(localizedText);
        setKeyAndArgs(key, args);
    }

    /**
     * Gets the current key used for localization.
     *
     * @return The current localization key.
     */
    public String getKey() {
        return localizedText.getTranslationKey();
    }

    /**
     * Sets the key and refreshes the text using the existing formatting arguments.
     * The backing property also refreshes the text when the language changes.
     *
     * @param key The localization key
     */
    public void setKey(final String key) {
        localizedText.setTranslationKey(key);
    }

    /**
     * Sets the arguments to be used for localization.
     *
     * @param args The arguments to be used for localization.
     */
    public void setArgs(final Object... args) {
        localizedText.setTranslationArgs(args);
    }

    /**
     * Sets both the key and the arguments for localization.
     *
     * @param key The localization key.
     * @param args The arguments to be used for localization.
     */
    public void setKeyAndArgs(final String key, final Object... args) {
        localizedText.setTranslation(key, args);
    }
}
