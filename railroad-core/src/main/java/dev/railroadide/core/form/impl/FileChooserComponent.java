package dev.railroadide.core.form.impl;

import dev.railroadide.core.form.*;
import dev.railroadide.core.form.ui.FormFileChooser;
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * File picker counterpart to {@link DirectoryChooserComponent}.
 */
public class FileChooserComponent extends FormComponent<FormFileChooser, FileChooserComponent.Data, TextField, String> {
    public FileChooserComponent(String dataKey,
                                Data data,
                                FormComponentValidator<TextField> validator,
                                FormComponentChangeListener<TextField, String> listener,
                                Property<TextField> bindTextFieldTo,
                                Property<BrowseButton> bindBrowseButtonTo,
                                List<FormTransformer<TextField, String, ?>> transformers,
                                EventHandler<? super KeyEvent> keyTypedHandler,
                                @Nullable BooleanBinding visible) {
        super(dataKey, data, d -> new FormFileChooser(d.label, d.required, d.defaultPath, d.includeButton), validator, listener, transformers, visible);

        if (bindTextFieldTo != null) {
            bindTextFieldTo.bind(componentProperty().map(FormFileChooser::getPrimaryComponent).map(FormFileChooser.TextFieldWithButton::getTextField));
        }

        if (bindBrowseButtonTo != null) {
            bindBrowseButtonTo.bind(componentProperty().map(FormFileChooser::getPrimaryComponent).map(FormFileChooser.TextFieldWithButton::getBrowseButton));
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
                ChangeListener<String> changeListener = (observable1, oldValue1, newValue1) ->
                    listener.changed(newValue.getPrimaryComponent().getTextField(), observable1, oldValue1, newValue1);
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
            .addListener((observable, oldValue, newValue) ->
                formData.addProperty(dataKey, newValue));

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

        public Builder(@NotNull String dataKey, @NotNull String label) {
            this.dataKey = dataKey;
            this.data = new Data(label);
        }

        @Override
        public String dataKey() {
            return dataKey;
        }

        public Builder defaultPath(@Nullable String defaultPath) {
            data.defaultPath(defaultPath);
            return this;
        }

        public Builder defaultPath(@Nullable Path defaultPath) {
            data.defaultPath(defaultPath != null ? defaultPath.toString() : null);
            return this;
        }

        public Builder required(boolean required) {
            data.required(required);
            return this;
        }

        public Builder required() {
            return required(true);
        }

        public Builder includeButton(boolean includeButton) {
            data.includeButton(includeButton);
            return this;
        }

        public Builder bindTextFieldTo(Property<TextField> bindTextFieldTo) {
            this.bindTextFieldTo = bindTextFieldTo;
            return this;
        }

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
        public <X> Builder addTransformer(ObservableValue<TextField> fromComponent, Consumer<X> toComponentFunction, Function<String, X> valueMapper) {
            transformers.add(new FormTransformer<>(fromComponent, TextField::getText, toComponentFunction, valueMapper));
            return this;
        }

        @Override
        public <U extends Node, X> Builder addTransformer(ObservableValue<TextField> fromComponent, ObservableValue<U> toComponent, Function<String, X> valueMapper) {
            transformers.add(new FormTransformer<>(fromComponent, TextField::getText, value -> {
                if (toComponent.getValue() instanceof TextField target) {
                    target.setText(value.toString());
                } else {
                    throw new IllegalArgumentException("Unsupported component type: " + toComponent.getValue().getClass().getName());
                }
            }, valueMapper));
            return this;
        }

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
            return new FileChooserComponent(dataKey, data, validator, listener, bindTextFieldTo, bindBrowseButtonTo, transformers, keyTypedHandler, visible);
        }
    }

    public static class Data {
        private final String label;
        private String defaultPath;
        private boolean required;
        private boolean includeButton = true;

        public Data(@NotNull String label) {
            this.label = label;
        }

        public Data defaultPath(@Nullable String defaultPath) {
            this.defaultPath = defaultPath;
            return this;
        }

        public Data required(boolean required) {
            this.required = required;
            return this;
        }

        public Data includeButton(boolean includeButton) {
            this.includeButton = includeButton;
            return this;
        }
    }
}
