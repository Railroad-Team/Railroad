package dev.railroadide.railroad.ui.localized;

import javafx.scene.Node;
import javafx.scene.control.TitledPane;
import org.jetbrains.annotations.NotNull;

/** A titled pane with a localization property for translating its header. */
public class LocalizedTitledPane extends TitledPane {
    private final LocalizedTextProperty localizedText = new LocalizedTextProperty(this, "localizedText", null);

    /**
     * Creates an empty titled pane. This constructor does not bind the header text to the
     * localization property, so calling {@link #setKey(String, Object...)} alone does not update its header.
     */
    public LocalizedTitledPane() {
    }

    /**
     * Creates a pane with content and a translated header that updates when the language changes.
     *
     * @param content the node displayed inside the pane
     * @param titleKey the localization key for the header
     * @param args the arguments used to format the header translation
     */
    public LocalizedTitledPane(Node content, String titleKey, @NotNull Object... args) {
        super(titleKey, content);
        textProperty().bindBidirectional(localizedText);
        setKey(titleKey, args);
    }

    /**
     * Updates the backing localization property's key and formatting arguments. The header is
     * refreshed when its text is bound to that property, as in the content-taking constructor.
     *
     * @param key The localization key
     * @param args The args to be applied to the localized key
     */
    public void setKey(@NotNull String key, @NotNull Object... args) {
        localizedText.setTranslation(key, args);
    }
}
