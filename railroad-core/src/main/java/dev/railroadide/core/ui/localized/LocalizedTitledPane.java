package dev.railroadide.core.ui.localized;

import javafx.scene.Node;
import javafx.scene.control.TitledPane;
import org.jetbrains.annotations.NotNull;

public class LocalizedTitledPane extends TitledPane {

    private final LocalizedTextProperty localizedText = new LocalizedTextProperty(this, "localizedText", null);

    public LocalizedTitledPane() {
    }

    public LocalizedTitledPane(Node content, String titleKey, @NotNull Object... args) {
        super(titleKey, content);
        textProperty().bindBidirectional(localizedText);
        setKey(titleKey, args);
    }

    /**
     * Updates the key and args, and then updates the text of the titled pane.
     * Adds a listener to the current language property to update the text when the language changes.
     *
     * @param key  The localization key
     * @param args The args to be applied to the localized key
     */
    public void setKey(@NotNull String key, @NotNull Object... args) {
        localizedText.setTranslation(key, args);
    }
}
