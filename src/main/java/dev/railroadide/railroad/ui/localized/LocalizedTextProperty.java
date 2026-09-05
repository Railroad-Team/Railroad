package dev.railroadide.railroad.ui.localized;

import dev.railroadide.railroad.localization.L18n;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.Objects;

/**
 * A text property capable of localizing its content.
 * <p>
 * Bind a pre-existing text property to it to use its behavior.
 * Use a bidirectional binding if you plan on also using un-localized text.
 * Translations refresh when the language, key, or arguments change. Directly setting text to a value
 * different from the last translation disables language-driven refreshes until the key or arguments
 * change, or {@link #setTranslation(String, Object...)} is called.
 */
public class LocalizedTextProperty extends StringPropertyBase {
    private static final String DEFAULT_NAME = "";

    private final Object bean;
    private final String name;

    private final StringProperty translationKey;
    private final ListProperty<Object> translationArgs;
    private String translated;

    /**
     * Creates a property and immediately translates its initial key with the supplied arguments.
     *
     * @param bean the bean of this {@code StringProperty}
     * @param name the name of this {@code StringProperty}, or {@code null} for an empty name
     * @param initialValue the initial translation key, or {@code null} for a null text value
     * @param args optional args to format the localized string
     * @throws NullPointerException if the argument array is {@code null}
     */
    public LocalizedTextProperty(Object bean, String name, String initialValue, Object... args) {
        super("");
        this.bean = bean;
        this.name = (name == null) ? DEFAULT_NAME : name;

        this.translationKey = new SimpleStringProperty(this, "localizationKey", initialValue);
        this.translationArgs = new SimpleListProperty<Object>(this, "localizationArgs",
            FXCollections.observableArrayList(args));

        translated = null;

        initialize();
        updateTranslation(true);
    }

    /**
     * Installs listeners for language, key, and argument changes. Key and argument changes reactivate
     * translation after a direct text assignment; language changes preserve that inactive state.
     */
    protected void initialize() {
        L18n.currentLanguageProperty()
            .addListener(_ -> updateTranslation(false));

        translationKey.addListener(_ -> updateTranslation(true));
        translationArgs.addListener((ListChangeListener<Object>) _ -> updateTranslation(true));
    }

    /**
     * Indicates whether the property is activated.
     * The property is deactivated when it's value has been directly set.
     */
    private boolean activated = false;

    /**
     * Used to block update when updating more than one property at a time.
     */
    private boolean blockedUpdates = false;

    private void updateTranslation(boolean activate) {

        if (blockedUpdates)
            return;

        if (activate) {
            activated = true;
        }

        if (!activated)
            return;

        if (translationKey.get() != null) {
            this.translated = L18n.localize(translationKey.get(), translationArgs.get().toArray());

            set(this.translated);
        } else {
            this.translated = null;
            set(null);
        }
    }

    /** Disables automatic translation when the text differs from the last translated value. */
    @Override
    protected void invalidated() {
        if (!Objects.equals(get(), translated)) {
            activated = false;
        }
    }

    /**
     * Returns the owning bean supplied at construction.
     *
     * @return the owning bean, which may be {@code null}
     */
    @Override
    public Object getBean() {
        return bean;
    }

    /**
     * Returns the name supplied at construction, with {@code null} normalized to an empty string.
     *
     * @return the non-null property name
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Exposes the key property used to select the translation. Changes trigger a translation refresh.
     *
     * @return the writable translation-key property
     */
    public StringProperty translationKeyProperty() {
        return this.translationKey;
    }

    /**
     * Returns the key currently used to look up translated text.
     *
     * @return the translation key, or {@code null} when no key is set
     */
    public String getTranslationKey() {
        return translationKey.get();
    }

    /**
     * Changes the translation key, treating null or whitespace-only keys as no translation.
     * A changed key refreshes the text using the current arguments.
     *
     * @param translationKey the translation key, or {@code null} or blank to clear it
     */
    public void setTranslationKey(String translationKey) {
        if (translationKey == null || translationKey.trim().isEmpty()) {
            this.translationKey.set(null);
        } else {
            this.translationKey.set(translationKey);
        }
    }

    /**
     * Exposes the list property holding the translation's formatting arguments.
     * Changes to the list trigger a translation refresh.
     *
     * @return the writable translation-argument list property
     */
    public ListProperty<Object> translationArgsProperty() {
        return this.translationArgs;
    }

    /**
     * Returns the live list of formatting arguments used by the translation.
     *
     * @return the observable argument list; mutations refresh the translated text
     */
    public ObservableList<Object> getTranslationArgs() {
        return translationArgs.get();
    }

    /**
     * Replaces the formatting arguments. An empty array or a single null argument clears the list.
     *
     * @param args the replacement formatting arguments
     * @throws NullPointerException if the argument array is {@code null}
     */
    public void setTranslationArgs(Object... args) {
        if (args.length == 0 || (args.length == 1 && args[0] == null)) {
            this.translationArgs.clear();
        } else {
            this.translationArgs.setAll(args);
        }
    }

    /**
     * Updates the key and arguments together, then translates once and reactivates automatic updates.
     *
     * @param translationKey the translation key, or {@code null} or blank to clear it
     * @param args the replacement formatting arguments; an empty array or single null clears them
     * @throws NullPointerException if the argument array is {@code null}
     */
    public void setTranslation(String translationKey, Object... args) {
        blockedUpdates = true;
        setTranslationKey(translationKey);
        setTranslationArgs(args);
        blockedUpdates = false;

        updateTranslation(true);
    }

}
