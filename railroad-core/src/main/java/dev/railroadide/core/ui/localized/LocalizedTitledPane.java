package dev.railroadide.core.ui.localized;

import dev.railroadide.core.localization.LocalizationService;
import dev.railroadide.core.utility.ServiceLocator;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.TitledPane;
import org.jetbrains.annotations.NotNull;

public class LocalizedTitledPane extends TitledPane {
    private final ObservableList<Object> args = FXCollections.observableArrayList();
    private String key;

    public LocalizedTitledPane() {}

    public LocalizedTitledPane(Node content, String titleKey, @NotNull Object... args) {
        super(titleKey, content);
        setKey(key, args);
    }

    /**
     * Updates the key and args, and then updates the text of the titled pane.
     * Adds a listener to the current language property to update the text when the language changes.
     *
     * @param key  The localization key
     * @param args The args to be applied to the localized key
     */
    public void setKey(@NotNull String key, @NotNull Object... args) {
        this.args.setAll(args);
        this.key = key;

        // Only set up localization if the key is not empty
        if (key != null && !key.trim().isEmpty()) {
            LocalizationService service = ServiceLocator.getService(LocalizationService.class);
            service.currentLanguageProperty().addListener((observable, oldValue, newValue) ->
                setText(service.get(key, args)));
            setText(service.get(this.key, args));
        } else {
            // Clear the text if the key is empty
            setText("");
        }
    }
}
