package dev.railroadide.core.ui.localized;

import javafx.scene.control.TableColumn;

/**
 * An extension of the JavaFX TableColumn that allows for the TableColumn's label to be localised.
 */
public class LocalizedTableColumn<S, T> extends TableColumn<S, T> {
    
    private final LocalizedTextProperty localizedText = new LocalizedTextProperty(this, "localizedText", null);

    /**
     * Sets the key and then the set the text to the localized key.
     *
     * @param translationKey The key to be localized
     * @param args Optional arguments to format the localized string
     */
    public LocalizedTableColumn(final String translationKey, Object... args) {
        super();
        textProperty().bindBidirectional(localizedText);
        setKey(translationKey);
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
     * Sets the key and then updates the text of the label.
     * Adds a listener to the current language property to update the text when the language changes.
     *
     * @param translationKey The localization key
     */
    public void setKey(final String translationKey) {
        localizedText.setTranslationKey(translationKey);
    }
}
