package dev.railroadide.core.form.impl;

import dev.railroadide.core.form.*;
import dev.railroadide.core.form.ui.FormDirectoryChooser;
import dev.railroadide.core.ui.BrowseButton;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A form component that represents a directory chooser.
 * Can be constructed using {@link FormComponent#directoryChooser(String, String)} or {@link Builder}.
 *
 * @see FormComponent
 * @see FormComponent#directoryChooser(String, String)
 * @see Builder
 */
public class DirectoryChooserComponent extends FormComponent<FormDirectoryChooser, DirectoryChooserComponent.Data, TextField, String> {
    /**
     * Constructs a new directory chooser component.
     *
     * @param dataKey            the key to store the data in the form data
     * @param data               the data for the directory chooser
     * @param validator          the validator for the directory chooser
     * @param listener           the listener for the directory chooser
     * @param bindTextFieldTo    the property to bind the text field to
     * @param bindBrowseButtonTo the property to bind the browse button to
     * @param transformers       the transformers for the directory chooser
     * @param keyTypedHandler    the key typed handler for the directory chooser
     * @param visible            the visibility of the directory chooser
     */
    public DirectoryChooserComponent(String dataKey, Data data, FormComponentValidator<TextField> validator, FormComponentChangeListener<TextField, String> listener, Property<TextField> bindTextFieldTo, Property<BrowseButton> bindBrowseButtonTo, List<FormTransformer<TextField, String, ?>> transformers, EventHandler<? super KeyEvent> keyTypedHandler, @Nullable BooleanBinding visible) {
        super(dataKey, data, dataCurrent -> new FormDirectoryChooser(dataCurrent.label, dataCurrent.required, dataCurrent.defaultPath, dataCurrent.includeButton), validator, listener, transformers, visible);

        if (bindTextFieldTo != null) {
            bindTextFieldTo.bind(componentProperty().map(FormDirectoryChooser::getPrimaryComponent).map(FormDirectoryChooser.TextFieldWithButton::getTextField));
        }

        if (bindBrowseButtonTo != null) {
            bindBrowseButtonTo.bind(componentProperty().map(FormDirectoryChooser::getPrimaryComponent).map(FormDirectoryChooser.TextFieldWithButton::getBrowseButton));
        }

        if (keyTypedHandler != null) {
            componentProperty().get().getPrimaryComponent().addEventHandler(KeyEvent.KEY_TYPED, keyTypedHandler);

            componentProperty().addListener((observable, oldValue, newValue) -> {
                if (oldValue != null) {
                    oldValue.getPrimaryComponent().removeEventHandler(KeyEvent.KEY_TYPED, keyTypedHandler);
                }

                if (newValue != null) {
                    newValue.getPrimaryComponent().addEventHandler(KeyEvent.KEY_TYPED, keyTypedHandler);
                }
            });
        }
    }

    @Override
    public ObservableValue<TextField> getValidationNode() {
        return componentProperty().map(FormDirectoryChooser::getPrimaryComponent).map(FormDirectoryChooser.TextFieldWithButton::getTextField);
    }

    @Override
    protected void applyListener(FormComponentChangeListener<TextField, String> listener) {
        AtomicReference<ChangeListener<String>> listenerRef = new AtomicReference<>();
        componentProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.getPrimaryComponent().getTextField().textProperty().removeListener(listenerRef.get());
            }

            if (newValue != null) {
                listenerRef.set((observable1, oldValue1, newValue1) ->
                        listener.changed(newValue.getPrimaryComponent().getTextField(), observable1, oldValue1, newValue1));

                newValue.getPrimaryComponent().getTextField().textProperty().addListener(listenerRef.get());
            }
        });
    }

    @Override
    protected void bindToFormData(FormData formData) {
        componentProperty()
                .map(FormDirectoryChooser::getPrimaryComponent)
                .map(FormDirectoryChooser.TextFieldWithButton::getTextField)
                .flatMap(TextField::textProperty)
                .addListener((observable, oldValue, newValue) ->
                        formData.addProperty(dataKey, newValue));

        formData.addProperty(dataKey, componentProperty()
                .map(FormDirectoryChooser::getPrimaryComponent)
                .map(FormDirectoryChooser.TextFieldWithButton::getTextField)
                .map(TextField::getText)
                .orElse(getData().defaultPath)
                .getValue());
    }

    @Override
    public void reset() {
        getComponent().getPrimaryComponent().getTextField().setText(getData().defaultPath);
    }

    /**
     * A builder for constructing a {@link DirectoryChooserComponent}.
     */
    public static class Builder {
        private final String dataKey;
        private final Data data;
        private final List<FormTransformer<TextField, String, ?>> transformers = new ArrayList<>();
        private FormComponentValidator<TextField> validator = null;
        private FormComponentChangeListener<TextField, String> listener = null;
        private Property<TextField> bindTextFieldTo;
        private Property<BrowseButton> bindBrowseButtonTo;
        private EventHandler<? super KeyEvent> keyTypedHandler;
        private BooleanBinding visible;

        /**
         * Constructs a new builder for a directory chooser component.
         *
         * @param dataKey the key to store the data in the form data
         * @param label   the label for the directory chooser
         */
        public Builder(@NotNull String dataKey, @NotNull String label) {
            this.dataKey = dataKey;
            this.data = new Data(label);
        }

        /**
         * Sets the default path for the directory chooser.
         *
         * @param defaultPath the default path for the directory chooser
         * @return this builder
         */
        public Builder defaultPath(@Nullable String defaultPath) {
            this.data.defaultPath(defaultPath);
            return this;
        }

        /**
         * Sets whether the directory chooser is required.
         *
         * @param required whether the directory chooser is required
         * @return this builder
         */
        public Builder required(boolean required) {
            this.data.required(required);
            return this;
        }

        /**
         * Sets the directory chooser as required.
         *
         * @return this builder
         */
        public Builder required() {
            return required(true);
        }

        /**
         * Sets whether the directory chooser should include a browse button.
         *
         * @param includeButton whether the directory chooser should include a browse button
         * @return this builder
         */
        public Builder includeButton(boolean includeButton) {
            this.data.includeButton(includeButton);
            return this;
        }

        /**
         * Sets the validator for the directory chooser.
         *
         * @param validator the validator
         * @return this builder
         */
        public Builder validator(@NotNull FormComponentValidator<TextField> validator) {
            this.validator = validator;
            return this;
        }

        /**
         * Sets the listener for the directory chooser.
         *
         * @param listener the listener
         * @return this builder
         */
        public Builder listener(@NotNull FormComponentChangeListener<TextField, String> listener) {
            this.listener = listener;
            return this;
        }

        /**
         * Binds the text field to a property.
         *
         * @param bindTextFieldTo the property to bind the text field to
         * @return this builder
         */
        public Builder bindTextFieldTo(@NotNull Property<TextField> bindTextFieldTo) {
            this.bindTextFieldTo = bindTextFieldTo;
            return this;
        }

        /**
         * Binds the browse button to a property.
         *
         * @param bindBrowseButtonTo the property to bind the browse button to
         * @return this builder
         */
        public Builder bindBrowseButtonTo(@NotNull Property<BrowseButton> bindBrowseButtonTo) {
            this.bindBrowseButtonTo = bindBrowseButtonTo;
            return this;
        }

        /**
         * Adds a transformer to the directory chooser.
         *
         * @param fromComponent       the observable value of the component to transform
         * @param toComponentFunction the function to set the value of the component
         * @param valueMapper         the function to map the value
         * @param <W>                 the type of the component
         * @return this builder
         */
        public <W> Builder addTransformer(ObservableValue<TextField> fromComponent, Consumer<W> toComponentFunction, Function<String, W> valueMapper) {
            this.transformers.add(new FormTransformer<>(fromComponent, TextField::getText, toComponentFunction, valueMapper));
            return this;
        }

        /**
         * Adds a transformer to the directory chooser.
         *
         * @param fromComponent the observable value of the component to transform
         * @param toComponent   the observable value of the component to set the value to
         * @param valueMapper   the function to map the value
         * @param <U>           the type of the component
         * @param <W>           the type of the value
         * @return this builder
         */
        public <U extends Node, W> Builder addTransformer(ObservableValue<TextField> fromComponent, ObservableValue<U> toComponent, Function<String, W> valueMapper) {
            this.transformers.add(new FormTransformer<>(fromComponent, TextField::getText, value -> {
                if (toComponent.getValue() instanceof TextField textField) {
                    textField.setText(value.toString());
                } else {
                    throw new IllegalArgumentException("Unsupported component type: " + toComponent.getValue().getClass().getName());
                }
            }, valueMapper));
            return this;
        }

        /**
         * Sets the key typed handler for the directory chooser.
         *
         * @param keyTypedHandler the key typed handler
         * @return this builder
         */
        public Builder keyTypedHandler(EventHandler<? super KeyEvent> keyTypedHandler) {
            this.keyTypedHandler = keyTypedHandler;
            return this;
        }

        /**
         * Sets the visibility of the directory chooser.
         *
         * @param visible the visibility
         * @return this builder
         */
        public Builder visible(BooleanBinding visible) {
            this.visible = visible;
            return this;
        }

        /**
         * Builds the directory chooser component.
         *
         * @return the directory chooser component
         */
        public DirectoryChooserComponent build() {
            return new DirectoryChooserComponent(dataKey, data, validator, listener, bindTextFieldTo, bindBrowseButtonTo, transformers, keyTypedHandler, visible);
        }
    }

    /**
     * The data for the directory chooser.
     */
    public static class Data {
        private final String label;
        private String defaultPath = null;
        private boolean required = false;
        private boolean includeButton = true;

        /**
         * Constructs a new data for the directory chooser.
         *
         * @param label the label for the directory chooser
         */
        public Data(@NotNull String label) {
            this.label = label;
        }

        /**
         * Sets the default path for the directory chooser.
         *
         * @param defaultPath the default path for the directory chooser
         * @return this data
         */
        public Data defaultPath(@Nullable String defaultPath) {
            this.defaultPath = defaultPath;
            return this;
        }

        /**
         * Sets whether the directory chooser is required.
         *
         * @param required whether the directory chooser is required
         * @return this data
         */
        public Data required(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * Sets whether the directory chooser should include a browse button.
         *
         * @param includeButton whether the directory chooser should include a browse button
         * @return this data
         */
        public Data includeButton(boolean includeButton) {
            this.includeButton = includeButton;
            return this;
        }
    }
}
