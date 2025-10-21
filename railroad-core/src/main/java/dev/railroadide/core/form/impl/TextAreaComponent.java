package dev.railroadide.core.form.impl;

import dev.railroadide.core.form.*;
import dev.railroadide.core.form.ui.FormTextArea;
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

/**
 * A form component that represents a text area.
 * Can be constructed using {@link FormComponent#textArea(String, String)} or {@link Builder}.
 *
 * @see FormComponent
 * @see FormComponent#textArea(String, String)
 * @see Builder
 */
public class TextAreaComponent extends FormComponent<FormTextArea, TextAreaComponent.Data, TextArea, String> {
    /**
     * Constructs a new text area component.
     *
     * @param dataKey         the key to store the data in the form data
     * @param data            the data for the text area
     * @param validator       the validator for the text area
     * @param listener        the listener for the text area
     * @param bindTextAreaTo  the property to bind the text area to
     * @param transformers    the transformers for the text area
     * @param keyTypedHandler the key typed handler for the text area
     * @param visible         the visibility of the text area
     */
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

    @Override
    public void reset() {
        getComponent().getPrimaryComponent().setText(getData().text);
    }

    /**
     * A builder for constructing a {@link TextAreaComponent}.
     */
    public static class Builder implements FormComponentBuilder<TextAreaComponent, TextArea, String, Builder> {
        private final String dataKey;
        private final Data data;
        private final List<FormTransformer<TextArea, String, ?>> transformers = new ArrayList<>();
        private FormComponentValidator<TextArea> validator;
        private FormComponentChangeListener<TextArea, String> listener;
        private Property<TextArea> bindTextAreaTo;
        private EventHandler<? super KeyEvent> keyTypedHandler;
        private BooleanBinding visible;

        /**
         * Constructs a new builder for a text area component.
         *
         * @param dataKey the key to store the data in the form data
         * @param label   the label for the text area
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
         * Sets whether the text area is required.
         *
         * @param required whether the text area is required
         * @return this builder
         */
        public Builder required(boolean required) {
            data.required = required;
            return this;
        }

        /**
         * Sets the text area to be required.
         *
         * @return this builder
         */
        public Builder required() {
            return required(true);
        }

        /**
         * Sets the text of the text area.
         *
         * @param text the text of the text area
         * @return this builder
         */
        public Builder text(String text) {
            data.text = text;
            return this;
        }

        /**
         * Sets the prompt text of the text area.
         *
         * @param promptText the prompt text of the text area
         * @return this builder
         */
        public Builder promptText(String promptText) {
            data.promptText = promptText;
            return this;
        }

        /**
         * Sets whether the text area is editable.
         *
         * @param editable whether the text area is editable
         * @return this builder
         */
        public Builder editable(boolean editable) {
            data.editable = editable;
            return this;
        }

        /**
         * Sets whether the text area is resizable (i.e. if it should automatically (vertically) resize to fit its content).
         *
         * @param resize whether the text area is resizable
         * @return this builder
         */
        public Builder resize(boolean resize) {
            data.resize = resize;
            return this;
        }

        /**
         * Sets whether the text area should wrap text.
         *
         * @param wrapText whether the text area should wrap text
         * @return this builder
         */
        public Builder wrapText(boolean wrapText) {
            data.wrapText = wrapText;
            return this;
        }

        /**
         * Sets whether the text area should translate its prompt text.
         *
         * @param translate whether the text area should translate its prompt text
         * @return this builder
         */
        public Builder translate(boolean translate) {
            data.translate = translate;
            return this;
        }

        /**
         * Sets the validator for the text area.
         *
         * @param validator the validator
         * @return this builder
         */
        @Override
        public Builder validator(FormComponentValidator<TextArea> validator) {
            this.validator = validator;
            return this;
        }

        /**
         * Sets the listener for the text area.
         *
         * @param listener the listener
         * @return this builder
         */
        @Override
        public Builder listener(FormComponentChangeListener<TextArea, String> listener) {
            this.listener = listener;
            return this;
        }

        /**
         * Binds the text area to a property.
         *
         * @param bindTextAreaTo the property to bind the text area to
         * @return this builder
         */
        public Builder bindTextAreaTo(Property<TextArea> bindTextAreaTo) {
            this.bindTextAreaTo = bindTextAreaTo;
            return this;
        }

        /**
         * Adds a transformer to the text area.
         *
         * @param fromComponent       the observable value of the component to transform
         * @param toComponentFunction the function to set the value of the component
         * @param valueMapper         the function to map the value to the desired type
         * @param <W>                 the type of the component
         * @return this builder
         */
        @Override
        public <X> Builder addTransformer(ObservableValue<TextArea> fromComponent, Consumer<X> toComponentFunction, Function<String, X> valueMapper) {
            this.transformers.add(new FormTransformer<>(fromComponent, TextArea::getText, toComponentFunction, valueMapper));
            return this;
        }

        /**
         * Adds a transformer to the text area.
         *
         * @param fromComponent the observable value of the component to transform
         * @param toComponent   the component to set the value to
         * @param valueMapper   the function to map the value to the desired type
         * @param <U>           the type of the component
         * @param <W>           the type of the value
         * @return this builder
         */
        @Override
        public <U extends Node, X> Builder addTransformer(ObservableValue<TextArea> fromComponent, ObservableValue<U> toComponent, Function<String, X> valueMapper) {
            this.transformers.add(new FormTransformer<>(fromComponent, TextArea::getText, value -> {
                if (toComponent.getValue() instanceof TextArea textArea) {
                    textArea.setText(value.toString());
                } else {
                    throw new IllegalArgumentException("Unsupported component type: " + toComponent.getValue().getClass().getName());
                }
            }, valueMapper));
            return this;
        }

        /**
         * Sets the key typed handler for the text area.
         *
         * @param keyTypedHandler the key typed handler
         * @return this builder
         */
        public Builder keyTypedHandler(EventHandler<? super KeyEvent> keyTypedHandler) {
            this.keyTypedHandler = keyTypedHandler;
            return this;
        }

        /**
         * Sets the visibility of the text area.
         *
         * @param visible the visibility
         * @return this builder
         */
        @Override
        public Builder visible(BooleanBinding visible) {
            this.visible = visible;
            return this;
        }

        /**
         * Builds the text area component.
         *
         * @return the text area component
         */
        @Override
        public TextAreaComponent build() {
            return new TextAreaComponent(dataKey, data, validator, listener, bindTextAreaTo, transformers, keyTypedHandler, visible);
        }
    }

    /**
     * The data for the text area.
     */
    public static class Data {
        public final String label;
        public boolean required;
        public String text = "";
        public String promptText;
        public boolean editable = true;
        public boolean resize;
        public boolean wrapText;
        public boolean translate = true;

        /**
         * Constructs a new data for the text area.
         *
         * @param label the label for the text area
         */
        public Data(String label) {
            this.label = label;
        }

        /**
         * Sets whether the text area is required.
         *
         * @param required whether the text area is required
         * @return this data
         */
        public Data required(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * Sets the text of the text area.
         *
         * @param text the text of the text area
         * @return this data
         */
        public Data text(String text) {
            this.text = text;
            return this;
        }

        /**
         * Sets the prompt text of the text area.
         *
         * @param promptText the prompt text of the text area
         * @return this data
         */
        public Data promptText(String promptText) {
            this.promptText = promptText;
            return this;
        }

        /**
         * Sets whether the text area is editable.
         *
         * @param editable whether the text area is editable
         * @return this data
         */
        public Data editable(boolean editable) {
            this.editable = editable;
            return this;
        }

        /**
         * Sets whether the text area is resizable (i.e. if it should automatically (vertically) resize to fit its content).
         *
         * @param resize whether the text area is resizable
         * @return this data
         */
        public Data resize(boolean resize) {
            this.resize = resize;
            return this;
        }

        /**
         * Sets whether the text area should wrap text.
         *
         * @param wrapText whether the text area should wrap text
         * @return this data
         */
        public Data wrapText(boolean wrapText) {
            this.wrapText = wrapText;
            return this;
        }

        /**
         * Sets whether the text area should translate its prompt text.
         *
         * @param translate whether the text area should translate its prompt text
         * @return this data
         */
        public Data translate(boolean translate) {
            this.translate = translate;
            return this;
        }
    }
}
