package dev.railroadide.core.ui.localized;

import dev.railroadide.core.localization.LocalizationService;
import dev.railroadide.core.utility.ServiceLocator;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * An extension of the JavaFX Label that allows for the Label's text to be localised.
 */
@Getter
public class LocalizedLabel extends Label {
    private final ObservableList<Object> args = FXCollections.observableArrayList();
    private String key;

    /**
     * Creates a new LocalizedLabel and sets the key and args
     *
     * @param key  The localization key
     * @param args The args to be applied to the localization key
     */
    public LocalizedLabel(@NotNull String key, @NotNull Object... args) {
        super();
        setKey(key, args);
    }

    /**
     * Updates the key and args, and then updates the text of the label.
     * Adds a listener to the current language property to update the text when the language changes.
     *
     * @param key  The localization key
     * @param args The args to be applied to the localized key
     */
    public void setKey(@NotNull String key, @NotNull Object... args) {
        this.args.setAll(args);
        this.key = key;

        if (key != null && !key.trim().isEmpty()) {
            LocalizationService service = ServiceLocator.getService(LocalizationService.class);
            service.currentLanguageProperty().addListener((observable, oldValue, newValue) ->
                setText(service.get(key, args)));
            setText(service.get(this.key, args));
        } else {
            setText("");
        }
    }
}
