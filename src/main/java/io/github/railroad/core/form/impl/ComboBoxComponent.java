package io.github.railroad.core.form.impl;

import io.github.railroad.core.form.*;
import io.github.railroad.core.form.ui.FormComboBox;
import io.github.railroad.core.utility.ComboBoxConverter;
import io.github.railroad.core.utility.FromStringFunction;
import io.github.railroad.core.utility.ToStringFunction;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.util.Callback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A form component that represents a combobox.
 * Can be constructed using {@link FormComponent#comboBox(String, String, Class)} or {@link Builder}.
 *
 * @see FormComponent
 * @see FormComponent#comboBox(String, String, Class)
 * @see Builder
 */
public class ComboBoxComponent<T> extends FormComponent<FormComboBox<T>, ComboBoxComponent.Data<T>, ComboBox<T>, T> {
    private final Supplier<T> defaultValue;

    /**
     * Constructs a new combobox component.
     *
     * @param dataKey         the key to store the data in the form data
     * @param data            the data for the combobox
     * @param validator       the validator for the combobox
     * @param listener        the listener for the combobox
     * @param bindComboBoxTo  the property to bind the combobox to
     * @param transformers    the transformers for the combobox
     * @param keyTypedHandler the key typed handler for the combobox
     * @param visible         the visibility of the combobox
     * @param cellFactory     the cell factory for the combobox
     * @param buttonCell      the button cell for the combobox
     * @param defaultValue    the default value for the combobox
     */
    public ComboBoxComponent(String dataKey, Data<T> data, FormComponentValidator<ComboBox<T>> validator, FormComponentChangeListener<ComboBox<T>, T> listener, Property<ComboBox<T>> bindComboBoxTo, List<FormTransformer<ComboBox<T>, T, ?>> transformers, EventHandler<? super KeyEvent> keyTypedHandler, @Nullable BooleanBinding visible, Callback<ListView<T>, ListCell<T>> cellFactory, ListCell<T> buttonCell, Supplier<T> defaultValue) {
        super(dataKey, data, currentData -> {
            var formComboBox = new FormComboBox<>(currentData.label, currentData.required, currentData.items, currentData.editable, currentData.translate, currentData.keyFunction, currentData.valueOfFunction);
            if (!currentData.translate) {
                formComboBox.getPrimaryComponent().setConverter(new ComboBoxConverter<>(currentData.keyFunction, currentData.valueOfFunction));
            }

            if (cellFactory != null) {
                formComboBox.getPrimaryComponent().setCellFactory(cellFactory);
            }

            if (buttonCell != null) {
                formComboBox.getPrimaryComponent().setButtonCell(buttonCell);
            }

            if (defaultValue != null) {
                formComboBox.getPrimaryComponent().setValue(defaultValue.get());
            }

            return formComboBox;
        }, validator, listener, transformers, visible);

        if (dataProperty().get() != null && !dataProperty().get().translate) {
            componentProperty().get().getPrimaryComponent().setConverter(new ComboBoxConverter<>(data.keyFunction, data.valueOfFunction));
        }

        componentProperty().get().getPrimaryComponent().getEditor().setOnKeyTyped(keyTypedHandler);

        componentProperty().addListener((observable, oldValue, newValue) -> {
            Data<T> currentData = dataProperty().get();
            if (newValue != null && currentData != null && !currentData.translate) {
                newValue.getPrimaryComponent().setConverter(new ComboBoxConverter<>(currentData.keyFunction, currentData.valueOfFunction));
            }

            if (keyTypedHandler != null) {
                if (oldValue != null) {
                    oldValue.getPrimaryComponent().getEditor().removeEventHandler(KeyEvent.KEY_TYPED, keyTypedHandler);
                }

                if (newValue != null) {
                    newValue.getPrimaryComponent().getEditor().addEventHandler(KeyEvent.KEY_TYPED, keyTypedHandler);
                }
            }
        });

        if (bindComboBoxTo != null) {
            bindComboBoxTo.bind(componentProperty().map(FormComboBox::getPrimaryComponent));
        }

        this.defaultValue = defaultValue;
    }

    @Override
    public ObservableValue<ComboBox<T>> getValidationNode() {
        return componentProperty().map(FormComboBox::getPrimaryComponent);
    }

    @Override
    protected void applyListener(FormComponentChangeListener<ComboBox<T>, T> listener) {
        AtomicReference<ChangeListener<T>> listenerRef = new AtomicReference<>();
        componentProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.getPrimaryComponent().valueProperty().removeListener(listenerRef.get());
            }

            if (newValue != null) {
                listenerRef.set((observable1, oldValue1, newValue1) ->
                        listener.changed(newValue.getPrimaryComponent(), observable1, oldValue1, newValue1));

                newValue.getPrimaryComponent().valueProperty().addListener(listenerRef.get());
            }
        });
    }

    @Override
    protected void bindToFormData(FormData formData) {
        componentProperty()
                .map(FormComboBox::getPrimaryComponent)
                .flatMap(ComboBox::valueProperty)
                .addListener((observable, oldValue, newValue) ->
                        formData.add(dataKey, newValue));

        formData.add(dataKey, componentProperty()
                .map(FormComboBox::getPrimaryComponent)
                .map(ComboBox::getValue)
                .orElse(defaultValue == null ? null : defaultValue.get())
                .getValue());
    }

    @Override
    public void reset() {
        getComponent().getPrimaryComponent().setValue(this.defaultValue == null ? null : this.defaultValue.get());
    }

    /**
     * A builder for constructing a {@link ComboBoxComponent}.
     *
     * @param <T> the type of the combobox
     */
    public static class Builder<T> {
        private final String dataKey;
        private final Data<T> data;
        private final List<FormTransformer<ComboBox<T>, T, ?>> transformers = new ArrayList<>();
        private FormComponentValidator<ComboBox<T>> validator;
        private FormComponentChangeListener<ComboBox<T>, T> listener;
        private Property<ComboBox<T>> bindComboBoxTo;
        private EventHandler<? super KeyEvent> keyTypedHandler;
        private BooleanBinding visible;
        private Callback<ListView<T>, ListCell<T>> cellFactory;
        private ListCell<T> buttonCell;
        private Supplier<T> defaultValue;

        /**
         * Constructs a new combobox builder.
         *
         * @param dataKey the key to store the data in the form data
         * @param label   the label for the combobox
         */
        public Builder(@NotNull String dataKey, @NotNull String label) {
            this.dataKey = dataKey;
            this.data = new Data<>(label);
        }

        /**
         * Sets the items for the combobox.
         *
         * @param items the items for the combobox
         * @return this builder
         */
        public Builder<T> items(Collection<T> items) {
            data.items(items);
            return this;
        }

        /**
         * Sets whether the combobox is editable (i.e. allows custom input).
         *
         * @param editable whether the combobox is editable
         * @return this builder
         */
        public Builder<T> editable(boolean editable) {
            data.editable(editable);
            return this;
        }

        /**
         * Sets whether the combobox is required.
         *
         * @param required whether the combobox is required
         * @return this builder
         */
        public Builder<T> required(boolean required) {
            data.required(required);
            return this;
        }

        /**
         * Sets the combobox to be required.
         *
         * @return this builder
         */
        public Builder<T> required() {
            return required(true);
        }

        /**
         * Sets whether the combobox should translate its items.
         *
         * @param translate whether the combobox should translate its items
         * @return this builder
         */
        public Builder<T> translate(boolean translate) {
            data.translate(translate);
            return this;
        }

        /**
         * Sets the key function for the combobox.
         *
         * @param keyFunction the key function
         * @return this builder
         */
        public Builder<T> keyFunction(ToStringFunction<T> keyFunction) {
            data.keyFunction(keyFunction);
            return this;
        }

        /**
         * Sets the value of function for the combobox.
         *
         * @param valueOfFunction the value of function
         * @return this builder
         */
        public Builder<T> valueOfFunction(FromStringFunction<T> valueOfFunction) {
            data.valueOfFunction(valueOfFunction);
            return this;
        }

        /**
         * Sets the validator for the combobox.
         *
         * @param validator the validator
         * @return this builder
         */
        public Builder<T> validator(FormComponentValidator<ComboBox<T>> validator) {
            this.validator = validator;
            return this;
        }

        /**
         * Sets the listener for the combobox.
         *
         * @param listener the listener
         * @return this builder
         */
        public Builder<T> listener(FormComponentChangeListener<ComboBox<T>, T> listener) {
            this.listener = listener;
            return this;
        }

        /**
         * Binds the combobox to a property.
         *
         * @param bindComboBoxTo the property to bind the combobox to
         * @return this builder
         */
        public Builder<T> bindComboBoxTo(Property<ComboBox<T>> bindComboBoxTo) {
            this.bindComboBoxTo = bindComboBoxTo;
            return this;
        }

        /**
         * Adds a transformer to the combobox.
         *
         * @param fromComponent       the observable value of the component to transform
         * @param toComponentFunction the function to set the value of the component
         * @param valueMapper         the value mapper
         * @param <W>                 the type of the component
         * @return this builder
         */
        public <W> Builder<T> addTransformer(ObservableValue<ComboBox<T>> fromComponent, Consumer<W> toComponentFunction, Function<T, W> valueMapper) {
            this.transformers.add(new FormTransformer<>(fromComponent, ComboBox::getValue, toComponentFunction, valueMapper));
            return this;
        }

        /**
         * Adds a transformer to the combobox.
         *
         * @param fromComponent the observable value of the component to transform
         * @param toComponent   the component to set the value of
         * @param valueMapper   the value mapper
         * @param <U>           the type of the component
         * @param <W>           the type of the value
         * @return this builder
         * @throws IllegalArgumentException if the component type is unsupported
         * @implNote The supported component types are {@link TextField} and {@link ComboBox}.
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <U extends Node, W> Builder<T> addTransformer(ObservableValue<ComboBox<T>> fromComponent, ObservableValue<U> toComponent, Function<T, W> valueMapper) {
            this.transformers.add(new FormTransformer<>(fromComponent, ComboBox::getValue, value -> {
                if (toComponent.getValue() instanceof TextField textField) {
                    textField.setText(value.toString());
                } else if (toComponent.getValue() instanceof ComboBox comboBox) {
                    try {
                        if (value instanceof Collection<?> collection) {
                            comboBox.getItems().setAll(collection);
                        } else {
                            comboBox.setValue(value);
                        }
                    } catch (Exception ignored) {
                    }
                } else {
                    throw new IllegalArgumentException("Unsupported component type: " + toComponent.getValue().getClass().getName());
                }
            }, valueMapper));
            return this;
        }

        /**
         * Sets the key typed handler for the combobox.
         *
         * @param keyTypedHandler the key typed handler
         * @return this builder
         */
        public Builder<T> keyTypedHandler(EventHandler<? super KeyEvent> keyTypedHandler) {
            this.keyTypedHandler = keyTypedHandler;
            return this;
        }

        /**
         * Sets the visibility of the combobox.
         *
         * @param visible the visibility
         * @return this builder
         */
        public Builder<T> visible(BooleanBinding visible) {
            this.visible = visible;
            return this;
        }

        /**
         * Sets the cell factory for the combobox.
         *
         * @param cellFactory the cell factory
         * @return this builder
         */
        public Builder<T> cellFactory(Callback<ListView<T>, ListCell<T>> cellFactory) {
            this.cellFactory = cellFactory;
            return this;
        }

        /**
         * Sets the button cell for the combobox.
         *
         * @param buttonCell the button cell
         * @return this builder
         */
        public Builder<T> buttonCell(ListCell<T> buttonCell) {
            this.buttonCell = buttonCell;
            return this;
        }

        /**
         * Sets the default value for the combobox.
         *
         * @param defaultValue the default value
         * @return this builder
         */
        public Builder<T> defaultValue(Supplier<T> defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        /**
         * Builds the combobox component.
         *
         * @return the combobox component
         */
        public ComboBoxComponent<T> build() {
            return new ComboBoxComponent<>(dataKey, data, validator, listener, bindComboBoxTo, transformers, keyTypedHandler, visible, cellFactory, buttonCell, defaultValue);
        }
    }

    /**
     * The data for the combobox.
     *
     * @param <T> the type of the combobox
     */
    public static class Data<T> {
        private final String label;
        private List<T> items = new ArrayList<>();
        private boolean editable = false;
        private boolean required = false;
        private boolean translate = true;
        private ToStringFunction<T> keyFunction = Object::toString;
        private FromStringFunction<T> valueOfFunction = string -> null;

        /**
         * Constructs a new data for the combobox.
         *
         * @param label the label for the combobox
         */
        public Data(@NotNull String label) {
            this.label = label;
        }

        /**
         * Sets the items for the combobox.
         *
         * @param items the items for the combobox
         * @return this data
         */
        public Data<T> items(@NotNull Collection<T> items) {
            this.items = new ArrayList<>(items);
            return this;
        }

        /**
         * Sets whether the combobox is editable (i.e. allows custom input).
         *
         * @param editable whether the combobox is editable
         * @return this data
         */
        public Data<T> editable(boolean editable) {
            this.editable = editable;
            return this;
        }

        /**
         * Sets whether the combobox is required.
         *
         * @param required whether the combobox is required
         * @return this data
         */
        public Data<T> required(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * Sets whether the combobox should translate its items.
         *
         * @param translate whether the combobox should translate its items
         * @return this data
         */
        public Data<T> translate(boolean translate) {
            this.translate = translate;
            return this;
        }

        /**
         * Sets the key function for the combobox.
         *
         * @param keyFunction the key function
         * @return this data
         */
        public Data<T> keyFunction(ToStringFunction<T> keyFunction) {
            this.keyFunction = keyFunction;
            return this;
        }

        /**
         * Sets the value of function for the combobox.
         *
         * @param valueOfFunction the value of function
         * @return this data
         */
        public Data<T> valueOfFunction(FromStringFunction<T> valueOfFunction) {
            this.valueOfFunction = valueOfFunction;
            return this;
        }
    }
}
