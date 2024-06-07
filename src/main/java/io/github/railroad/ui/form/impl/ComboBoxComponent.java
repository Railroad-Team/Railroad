package io.github.railroad.ui.form.impl;

import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.form.FormComponent;
import io.github.railroad.ui.form.FormComponentChangeListener;
import io.github.railroad.ui.form.FormComponentValidator;
import io.github.railroad.ui.localized.LocalizedComboBox;
import io.github.railroad.utility.ComboBoxConverter;
import io.github.railroad.utility.FromStringFunction;
import io.github.railroad.utility.ToStringFunction;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ComboBoxComponent<T> extends FormComponent<ComboBoxComponent.FormComboBox<T>, ComboBoxComponent.Data<T>, ComboBox<T>, T> {
    public ComboBoxComponent(Data<T> data, FormComponentValidator<ComboBox<T>> validator, FormComponentChangeListener<ComboBox<T>, T> listener) {
        super(data, currentData -> {
            var formComboBox = new FormComboBox<>(currentData.label, currentData.items, currentData.editable, currentData.required, currentData.translate, currentData.keyFunction, currentData.valueOfFunction);
            if(!currentData.translate) {
                formComboBox.comboBox.setConverter(new ComboBoxConverter<>(currentData.keyFunction, currentData.valueOfFunction));
            }
            return formComboBox;
        }, validator, listener);

        componentProperty().addListener((observable, oldValue, newValue) -> {
            Data<T> currentData = dataProperty().get();
            if (newValue != null && currentData != null && !currentData.translate) {
                newValue.comboBox.setConverter(new ComboBoxConverter<>(currentData.keyFunction, currentData.valueOfFunction));
            }
        });
    }

    @Override
    public ObservableValue<ComboBox<T>> getValidationNode() {
        return componentProperty().map(FormComboBox::getComboBox);
    }

    @Override
    protected void applyListener(FormComponentChangeListener<ComboBox<T>, T> listener) {
        AtomicReference<ChangeListener<T>> listenerRef = new AtomicReference<>();
        componentProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.comboBox.valueProperty().removeListener(listenerRef.get());
            }

            if (newValue != null) {
                listenerRef.set((observable1, oldValue1, newValue1) ->
                        listener.changed(newValue.comboBox, observable1, oldValue1, newValue1));

                newValue.comboBox.valueProperty().addListener(listenerRef.get());
            }
        });
    }

    @Getter
    public static class FormComboBox<T> extends RRHBox {
        private final ComboBox<T> comboBox;
        private final Label label;

        public FormComboBox(@NotNull String label, List<T> items, boolean editable, boolean required, boolean translate, ToStringFunction<T> toStringFunction, FromStringFunction<T> fromStringFunction) {
            setSpacing(10);

            this.label = createLabel(this, label, required);

            this.comboBox = translate ? new LocalizedComboBox<>(toStringFunction, fromStringFunction) : new ComboBox<>();
            this.comboBox.getItems().addAll(items);
            this.comboBox.setEditable(editable);
            getChildren().add(this.comboBox);
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
