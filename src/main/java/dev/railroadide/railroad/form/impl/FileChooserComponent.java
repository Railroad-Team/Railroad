package dev.railroadide.railroad.form.impl;

import dev.railroadide.railroad.form.*;
import dev.railroadide.railroad.form.ui.FormFileChooser;
import dev.railroadide.railroad.ui.BrowseButton;
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A form component that lets the user enter or browse for a file path.
 *
 * <p>
 * The text field is used as the validation and form-data node. An optional
 * browse button opens a file chooser.
 * </p>
 */
public class FileChooserComponent extends FormComponent<FormFileChooser, FileChooserComponent.Data, TextField, String> {
    /**
     * Creates a file chooser component.
     *
     * @param dataKey the key under which the path is stored in form data
     * @param data the component configuration
     * @param validator the text-field validator
     * @param listener the text-field change listener
     * @param bindTextFieldTo the property to which the text field is bound
     * @param bindBrowseButtonTo the property to which the browse button is bound
     * @param transformers value transformers attached to the component
     * @param keyTypedHandler handler for key-typed events in the chooser
     * @param visible binding controlling component visibility
     */
    public FileChooserComponent(
        String dataKey,
        Data data,
        FormComponentValidator<TextField> validator,
        FormComponentChangeListener<TextField, String> listener,
        Property<TextField> bindTextFieldTo,
        Property<BrowseButton> bindBrowseButtonTo,
        List<FormTransformer<TextField, String, ?>> transformers,
        EventHandler<? super KeyEvent> keyTypedHandler,
        @Nullable BooleanBinding visible
    ) {
        super(dataKey, data, d -> new FormFileChooser(d.label, d.required, d.defaultPath, d.includeButton), validator,
            listener, transformers, visible);

        if (bindTextFieldTo != null) {
            bindTextFieldTo.bind(componentProperty().map(FormFileChooser::getPrimaryComponent)
                .map(FormFileChooser.TextFieldWithButton::getTextField));
        }

        if (bindBrowseButtonTo != null) {
            bindBrowseButtonTo.bind(componentProperty().map(FormFileChooser::getPrimaryComponent)
                .map(FormFileChooser.TextFieldWithButton::getBrowseButton));
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
        return componentProperty()
            .map(FormFileChooser::getPrimaryComponent)
            .map(FormFileChooser.TextFieldWithButton::getTextField);
    }

    @Override
    protected void applyListener(FormComponentChangeListener<TextField, String> listener) {
        AtomicReference<ChangeListener<String>> listenerRef = new AtomicReference<>();
        componentProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.getPrimaryComponent().getTextField().textProperty().removeListener(listenerRef.get());
            }

            if (newValue != null) {
                ChangeListener<String> changeListener = (observable1, oldValue1, newValue1) -> listener
                    .changed(newValue.getPrimaryComponent().getTextField(), observable1, oldValue1, newValue1);
                listenerRef.set(changeListener);
                newValue.getPrimaryComponent().getTextField().textProperty().addListener(changeListener);
            }
        });
    }

    @Override
    protected void bindToFormData(FormData formData) {
        componentProperty()
            .map(FormFileChooser::getPrimaryComponent)
            .map(FormFileChooser.TextFieldWithButton::getTextField)
            .flatMap(TextField::textProperty)
            .addListener((observable, oldValue, newValue) -> formData.addProperty(dataKey, newValue));

        formData.addProperty(dataKey, componentProperty()
            .map(FormFileChooser::getPrimaryComponent)
            .map(FormFileChooser.TextFieldWithButton::getTextField)
            .map(TextField::getText)
            .orElse(getData().defaultPath)
            .getValue());
    }

    @Override
    public void reset() {
        getComponent().getPrimaryComponent().getTextField().setText(getData().defaultPath);
    }

    /** Builds and configures a {@link FileChooserComponent}. */
    public static class Builder implements FormComponentBuilder<FileChooserComponent, TextField, String, Builder> {
        private final String dataKey;
        private final Data data;
        private final List<FormTransformer<TextField, String, ?>> transformers = new ArrayList<>();
        private FormComponentValidator<TextField> validator;
        private FormComponentChangeListener<TextField, String> listener;
        private Property<TextField> bindTextFieldTo;
        private Property<BrowseButton> bindBrowseButtonTo;
        private EventHandler<? super KeyEvent> keyTypedHandler;
        private BooleanBinding visible;

        /**
         * Creates a file chooser builder.
         *
         * @param dataKey the key under which the path is stored in form data
         * @param label the localization key for the field label
         */
        public Builder(@NotNull String dataKey, @NotNull String label) {
            this.dataKey = dataKey;
            this.data = new Data(label);
        }

        @Override
        public String dataKey() {
            return dataKey;
        }

        /**
         * Sets the initial path as a string.
         *
         * @param defaultPath the initial path, or {@code null} for no path
         * @return this builder
         */
        public Builder defaultPath(@Nullable String defaultPath) {
            data.defaultPath(defaultPath);
            return this;
        }

        /**
         * Sets the initial path.
         *
         * @param defaultPath the initial path, or {@code null} for no path
         * @return this builder
         */
        public Builder defaultPath(@Nullable Path defaultPath) {
            data.defaultPath(defaultPath != null ? defaultPath.toString() : null);
            return this;
        }

        /**
         * Sets whether a path is required.
         *
         * @param required whether the field is required
         * @return this builder
         */
        public Builder required(boolean required) {
            data.required(required);
            return this;
        }

        /**
         * Marks the path as required.
         *
         * @return this builder
         */
        public Builder required() {
            return required(true);
        }

        /**
         * Sets whether to include the file-browse button.
         *
         * @param includeButton whether the browse button should be included
         * @return this builder
         */
        public Builder includeButton(boolean includeButton) {
            data.includeButton(includeButton);
            return this;
        }

        /**
         * Binds the created text field to a property.
         *
         * @param bindTextFieldTo the target text-field property
         * @return this builder
         */
        public Builder bindTextFieldTo(Property<TextField> bindTextFieldTo) {
            this.bindTextFieldTo = bindTextFieldTo;
            return this;
        }

        /**
         * Binds the created browse button to a property.
         *
         * @param bindBrowseButtonTo the target browse-button property
         * @return this builder
         */
        public Builder bindBrowseButtonTo(Property<BrowseButton> bindBrowseButtonTo) {
            this.bindBrowseButtonTo = bindBrowseButtonTo;
            return this;
        }

        @Override
        public Builder validator(FormComponentValidator<TextField> validator) {
            this.validator = validator;
            return this;
        }

        @Override
        public Builder listener(FormComponentChangeListener<TextField, String> listener) {
            this.listener = listener;
            return this;
        }

        @Override
        public <X> Builder addTransformer(
            ObservableValue<TextField> fromComponent,
            Consumer<X> toComponentFunction,
            Function<String, X> valueMapper
        ) {
            transformers
                .add(new FormTransformer<>(fromComponent, TextField::getText, toComponentFunction, valueMapper));
            return this;
        }

        @Override
        public <U extends Node, X> Builder addTransformer(
            ObservableValue<TextField> fromComponent,
            ObservableValue<U> toComponent,
            Function<String, X> valueMapper
        ) {
            transformers.add(new FormTransformer<>(fromComponent, TextField::getText, value -> {
                if (toComponent.getValue() instanceof TextField target) {
                    target.setText(value.toString());
                } else
                    throw new IllegalArgumentException(
                        "Unsupported component type: " + toComponent.getValue().getClass().getName());
            }, valueMapper));
            return this;
        }

        /**
         * Sets the key-typed event handler for the text field.
         *
         * @param keyTypedHandler the event handler, or {@code null} to omit it
         * @return this builder
         */
        public Builder keyTypedHandler(EventHandler<? super KeyEvent> keyTypedHandler) {
            this.keyTypedHandler = keyTypedHandler;
            return this;
        }

        @Override
        public Builder visible(BooleanBinding visible) {
            this.visible = visible;
            return this;
        }

        @Override
        public FileChooserComponent build() {
            return new FileChooserComponent(dataKey, data, validator, listener, bindTextFieldTo, bindBrowseButtonTo,
                transformers, keyTypedHandler, visible);
        }
    }

    /** Mutable configuration used to create a file chooser component. */
    public static class Data {
        private final String label;
        private String defaultPath;
        private boolean required;
        private boolean includeButton = true;

        /**
         * Creates file chooser data.
         *
         * @param label the localization key for the field label
         */
        public Data(@NotNull String label) {
            this.label = label;
        }

        /**
         * Sets the initial path.
         *
         * @param defaultPath the initial path, or {@code null} for no path
         * @return this data object
         */
        public Data defaultPath(@Nullable String defaultPath) {
            this.defaultPath = defaultPath;
            return this;
        }

        /**
         * Sets whether a path is required.
         *
         * @param required whether the field is required
         * @return this data object
         */
        public Data required(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * Sets whether to include the file-browse button.
         *
         * @param includeButton whether the browse button should be included
         * @return this data object
         */
        public Data includeButton(boolean includeButton) {
            this.includeButton = includeButton;
            return this;
        }
    }
}
