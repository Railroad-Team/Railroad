package io.github.railroad.ui.form.impl;

import io.github.railroad.ui.form.*;
import io.github.railroad.ui.form.ui.FormTextArea;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public class TextAreaComponent extends FormComponent<FormTextArea, TextAreaComponent.Data, TextArea, String> {
    public TextAreaComponent(String dataKey, Data data, FormComponentValidator<TextArea> validator, FormComponentChangeListener<TextArea, String> listener, Property<TextArea> bindTextAreaTo, List<FormTransformer<TextArea, String, ?>> transformers, EventHandler<? super KeyEvent> keyTypedHandler, @Nullable BooleanBinding visible) {
        super(dataKey, data, currentData -> new FormTextArea(currentData.label, currentData.required, currentData.text, currentData.promptText, currentData.editable, currentData.resize, currentData.wrapText, currentData.translate), validator, listener, transformers, visible);

        if (bindTextAreaTo != null) {
            bindTextAreaTo.bind(componentProperty().map(FormTextArea::getPrimaryComponent));
        }

        if (keyTypedHandler != null) {
            componentProperty().get().getTextArea().addEventHandler(KeyEvent.KEY_TYPED, keyTypedHandler);

            componentProperty().addListener((observable, oldValue, newValue) -> {
                if (oldValue != null) {
                    oldValue.getTextArea().removeEventHandler(KeyEvent.KEY_TYPED, keyTypedHandler);
                }

                if (newValue != null) {
                    newValue.getTextArea().addEventHandler(KeyEvent.KEY_TYPED, keyTypedHandler);
                }
            });
        }
    }

    @Override
    public ObservableValue<TextArea> getValidationNode() {
        return componentProperty().map(FormTextArea::getPrimaryComponent);
    }

    @Override
    protected void applyListener(FormComponentChangeListener<TextArea, String> listener) {
        AtomicReference<ChangeListener<String>> listenerRef = new AtomicReference<>();
        componentProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.getTextArea().textProperty().removeListener(listenerRef.get());
            }

            if (newValue != null) {
                TextArea textArea = newValue.getTextArea();
                listenerRef.set((observable1, oldValue1, newValue1) ->
                        listener.changed(textArea, observable1, oldValue1, newValue1));
                textArea.textProperty().addListener(listenerRef.get());
            }
        });
    }

    @Override
    protected void bindToFormData(FormData formData) {
        componentProperty()
                .map(FormTextArea::getPrimaryComponent)
                .flatMap(TextArea::textProperty)
                .addListener((observable, oldValue, newValue) ->
                        formData.addProperty(dataKey, newValue));

        formData.addProperty(dataKey, componentProperty()
                .map(FormTextArea::getPrimaryComponent)
                .map(TextArea::getText)
                .orElse(getData().text)
                .getValue());
    }

    public static class Builder {
        private final String dataKey;
        private final Data data;
        private FormComponentValidator<TextArea> validator;
        private FormComponentChangeListener<TextArea, String> listener;
        private Property<TextArea> bindTextAreaTo;
        private final List<FormTransformer<TextArea, String, ?>> transformers = new ArrayList<>();
        private EventHandler<? super KeyEvent> keyTypedHandler;
        private BooleanBinding visible;

        public Builder(@NotNull String dataKey, @NotNull String label) {
            this.dataKey = dataKey;
            this.data = new Data(label);
        }

        public Builder required(boolean required) {
            data.required = required;
            return this;
        }

        public Builder required() {
            return required(true);
        }

        public Builder text(String text) {
            data.text = text;
            return this;
        }

        public Builder promptText(String promptText) {
            data.promptText = promptText;
            return this;
        }

        public Builder editable(boolean editable) {
            data.editable = editable;
            return this;
        }

        public Builder resize(boolean resize) {
            data.resize = resize;
            return this;
        }

        public Builder wrapText(boolean wrapText) {
            data.wrapText = wrapText;
            return this;
        }

        public Builder translate(boolean translate) {
            data.translate = translate;
            return this;
        }

        public Builder validator(FormComponentValidator<TextArea> validator) {
            this.validator = validator;
            return this;
        }

        public Builder listener(FormComponentChangeListener<TextArea, String> listener) {
            this.listener = listener;
            return this;
        }

        public Builder bindTextAreaTo(Property<TextArea> bindTextAreaTo) {
            this.bindTextAreaTo = bindTextAreaTo;
            return this;
        }

        public <W> TextAreaComponent.Builder addTransformer(ObservableValue<TextArea> fromComponent, Consumer<W> toComponentFunction, Function<String, W> valueMapper) {
            this.transformers.add(new FormTransformer<>(fromComponent, TextArea::getText, toComponentFunction, valueMapper));
            return this;
        }

        public <U extends Node, W> TextAreaComponent.Builder addTransformer(ObservableValue<TextArea> fromComponent, ObservableValue<U> toComponent, Function<String, W> valueMapper) {
            this.transformers.add(new FormTransformer<>(fromComponent, TextArea::getText, value -> {
                if (toComponent.getValue() instanceof TextArea textArea) {
                    textArea.setText(value.toString());
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

        public Builder visible(BooleanBinding visible) {
            this.visible = visible;
            return this;
        }

        public TextAreaComponent build() {
            return new TextAreaComponent(dataKey, data, validator, listener, bindTextAreaTo, transformers, keyTypedHandler, visible);
        }
    }

    public static class Data {
        public final String label;
        public boolean required;
        public String text = "";
        public String promptText;
        public boolean editable = true;
        public boolean resize;
        public boolean wrapText;
        public boolean translate = true;

        public Data(String label) {
            this.label = label;
        }

        public Data required(boolean required) {
            this.required = required;
            return this;
        }

        public Data text(String text) {
            this.text = text;
            return this;
        }

        public Data promptText(String promptText) {
            this.promptText = promptText;
            return this;
        }

        public Data editable(boolean editable) {
            this.editable = editable;
            return this;
        }

        public Data resize(boolean resize) {
            this.resize = resize;
            return this;
        }

        public Data wrapText(boolean wrapText) {
            this.wrapText = wrapText;
            return this;
        }

        public Data translate(boolean translate) {
            this.translate = translate;
            return this;
        }
    }
}
