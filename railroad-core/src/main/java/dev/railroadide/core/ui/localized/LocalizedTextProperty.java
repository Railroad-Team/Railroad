package dev.railroadide.core.ui.localized;

import dev.railroadide.core.localization.LocalizationService;
import dev.railroadide.core.utility.ServiceLocator;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.StringPropertyBase;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

/**
 * A text property capable of localizing its content.
 * 
 * Bind a pre-exiting text property to it to use its behavior.
 * Use a bidirectional binding if you plan on also using un-localized text.
 * 
 */
public class LocalizedTextProperty extends StringPropertyBase {

    private static final Object DEFAULT_BEAN = null;
    private static final String DEFAULT_NAME = "";

    private final Object bean;
    private final String name;

    private final StringProperty translationKey;
    private final ListProperty<Object> translationArgs;
    private String translated;

    /**
     * The constructor of {@code LocalizedTextProperty}
     *
     * @param bean         the bean of this {@code StringProperty}
     * @param name         the name of this {@code StringProperty}
     * @param initialValue the initial value of the wrapped value
     * @param args         optional args to format the localized string
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

    protected void initialize() {
        ServiceLocator.getService(LocalizationService.class)
                .currentLanguageProperty()
                .addListener($ -> updateTranslation(false));

        translationKey.addListener($ -> updateTranslation(true));
        translationArgs.addListener((ListChangeListener<Object>) $ -> updateTranslation(true));
    }

    /**
     * Indicates whether the property is activated.
     * The property is disactivated when it's value has been directly set.
     */
    private boolean activated = false;

    /**
     * Used to block update when updating more than one property at a time.
     */
    private boolean blockedUpdates = false;

    private void updateTranslation(boolean activate) {
        
        if (blockedUpdates)
            return;

        if (activate)
            activated = true;
        
        if (!activated)
            return;

        if (translationKey.get() != null) {
            var service = ServiceLocator.getService(LocalizationService.class);
            this.translated = service.get(translationKey.get(), translationArgs.get().toArray());

            set(this.translated);
        } else {
            this.translated = null;
            set(null);
        }
    }

    @Override
    protected void invalidated() {
        if (get() != translated) {
            activated = false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getBean() {
        return bean;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * @return A property containing this property's translationKey
     */
    public StringProperty translationKeyProperty() { return this.translationKey; }
    public String getTranslationKey() { return translationKey.get(); }
    public void setTranslationKey(String translationKey) {
        if (translationKey == null || translationKey.trim().isEmpty())
            this.translationKey.set(null);
        else
            this.translationKey.set(translationKey);
    }

    /**
     * @return A property containing this property's translation arguments
     */
    public ListProperty<Object> translationArgsProperty() { return this.translationArgs; }
    public ObservableList<Object> getTranslationArgs() { return translationArgs.get(); }
    public void setTranslationArgs(Object... args) {
        if (args.length == 0 || (args.length == 1 && args[0] == null))
            this.translationArgs.clear();
        else
            this.translationArgs.setAll(args);
    }

    public void setTranslation(String translationKey, Object... args) {
        blockedUpdates = true;
        setTranslationKey(translationKey);
        setTranslationArgs(args);
        blockedUpdates = false;

        updateTranslation(true);
    }

}