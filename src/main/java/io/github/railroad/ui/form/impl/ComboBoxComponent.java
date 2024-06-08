package io.github.railroad.ui.form.impl;

import io.github.railroad.ui.form.FormComponent;
import io.github.railroad.ui.form.FormComponentChangeListener;
import io.github.railroad.ui.form.FormComponentValidator;
import io.github.railroad.ui.form.ui.FormComboBox;
import io.github.railroad.utility.ComboBoxConverter;
import io.github.railroad.utility.FromStringFunction;
import io.github.railroad.utility.ToStringFunction;
import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.ComboBox;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ComboBoxComponent<T> extends FormComponent<FormComboBox<T>, ComboBoxComponent.Data<T>, ComboBox<T>, T> {
    public ComboBoxComponent(Data<T> data, FormComponentValidator<ComboBox<T>> validator, FormComponentChangeListener<ComboBox<T>, T> listener, Property<ComboBox<T>> bindComboBoxTo) {
        super(data, currentData -> {
            var formComboBox = new FormComboBox<>(currentData.label, currentData.required, currentData.items, currentData.editable, currentData.translate, currentData.keyFunction, currentData.valueOfFunction);
            if(!currentData.translate) {
                formComboBox.getPrimaryComponent().setConverter(new ComboBoxConverter<>(currentData.keyFunction, currentData.valueOfFunction));
            }
            return formComboBox;
        }, validator, listener);

        componentProperty().addListener((observable, oldValue, newValue) -> {
            Data<T> currentData = dataProperty().get();
            if (newValue != null && currentData != null && !currentData.translate) {
                newValue.getPrimaryComponent().setConverter(new ComboBoxConverter<>(currentData.keyFunction, currentData.valueOfFunction));
            }
        });

        if(bindComboBoxTo != null) {
            bindComboBoxTo.bind(componentProperty().map(FormComboBox::getPrimaryComponent));
        }
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

    public static class Builder<T> {
        private final Data<T> data;
        private FormComponentValidator<ComboBox<T>> validator;
        private FormComponentChangeListener<ComboBox<T>, T> listener;
        private Property<ComboBox<T>> bindComboBoxTo;

        public Builder(@NotNull String label) {
            this.data = new Data<>(label);
        }

        public Builder<T> items(Collection<T> items) {
            data.items(items);
            return this;
        }

        public Builder<T> editable(boolean editable) {
            data.editable(editable);
            return this;
        }

        public Builder<T> required(boolean required) {
            data.required(required);
            return this;
        }

        public Builder<T> translate(boolean translate) {
            data.translate(translate);
            return this;
        }

        public Builder<T> keyFunction(ToStringFunction<T> keyFunction) {
            data.keyFunction(keyFunction);
            return this;
        }

        public Builder<T> valueOfFunction(FromStringFunction<T> valueOfFunction) {
            data.valueOfFunction(valueOfFunction);
            return this;
        }

        public Builder<T> validator(FormComponentValidator<ComboBox<T>> validator) {
            this.validator = validator;
            return this;
        }

        public Builder<T> listener(FormComponentChangeListener<ComboBox<T>, T> listener) {
            this.listener = listener;
            return this;
        }

        public Builder<T> bindComboBoxTo(Property<ComboBox<T>> bindComboBoxTo) {
            this.bindComboBoxTo = bindComboBoxTo;
            return this;
        }

        public Builder<T> required() {
            return required(true);
        }

        public ComboBoxComponent<T> build() {
            return new ComboBoxComponent<>(data, validator, listener, bindComboBoxTo);
        }
    }

    public static class Data<T> {
        private final String label;
        private List<T> items;
        private boolean editable = true;
        private boolean required = false;
        private boolean translate = true;
        private ToStringFunction<T> keyFunction = Object::toString;
        private FromStringFunction<T> valueOfFunction = string -> null;

        public Data(@NotNull String label) {
            this.label = label;
        }

        public Data<T> items(Collection<T> items) {
            this.items = new ArrayList<>(items);
            return this;
        }

        public Data<T> editable(boolean editable) {
            this.editable = editable;
            return this;
        }

        public Data<T> required(boolean required) {
            this.required = required;
            return this;
        }

        public Data<T> translate(boolean translate) {
            this.translate = translate;
            return this;
        }

        public Data<T> keyFunction(ToStringFunction<T> keyFunction) {
            this.keyFunction = keyFunction;
            return this;
        }

        public Data<T> valueOfFunction(FromStringFunction<T> valueOfFunction) {
            this.valueOfFunction = valueOfFunction;
            return this;
        }
    }
}
