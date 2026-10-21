package dev.railroadide.railroad.ui.localized;

import javafx.scene.control.TableColumn;

/**
 * An extension of the JavaFX TableColumn that allows for the TableColumn's label to be localised.
 *
 * @param <S> the type of row items in the table
 * @param <T> the type of values displayed by this column
 */
public class LocalizedTableColumn<S, T> extends TableColumn<S, T> {

    private final LocalizedTextProperty localizedText = new LocalizedTextProperty(this, "localizedText", null);

    /**
     * Creates a column with a translated header that updates when the language changes.
     *
     * @param translationKey The key to be localized
     * @param args reserved formatting arguments; this constructor currently does not apply them
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
     * Sets the key and updates the column header. The backing property refreshes the header
     * when the language changes.
     *
     * @param translationKey The localization key
     */
    public void setKey(final String translationKey) {
        localizedText.setTranslationKey(translationKey);
    }
}
