package io.github.railroad.ui.form.impl;

import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.form.FormComponent;
import io.github.railroad.utility.ComboBoxConverter;
import io.github.railroad.utility.FromStringFunction;
import io.github.railroad.utility.ToStringFunction;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class ComboBoxComponent<T> extends FormComponent<ComboBoxComponent.FormComboBox<T>, ComboBoxComponent.Data<T>> {
    public ComboBoxComponent(Supplier<Data<T>> dataSupplier, ToStringFunction<T> toStringFunction, FromStringFunction<T> fromStringFunction) {
        super(dataSupplier, data -> {
            var formComboBox = new FormComboBox<>(data.label, data.items, data.editable, data.required);
            formComboBox.comboBox.setConverter(new ComboBoxConverter<>(toStringFunction, fromStringFunction));
            return formComboBox;
        });

        componentProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                newValue.comboBox.setConverter(new ComboBoxConverter<>(toStringFunction, fromStringFunction));
            }
        });
    }

    public ComboBoxComponent(Supplier<Data<T>> dataSupplier) {
        this(dataSupplier, Object::toString, string -> null);
    }

    public static class FormComboBox<T> extends RRHBox {
        private final ComboBox<T> comboBox;
        private final Label label;

        public FormComboBox(@NotNull String label, List<T> items, boolean editable, boolean required) {
            setSpacing(10);

            this.label = new Label(label);
            this.comboBox = new ComboBox<>(FXCollections.observableArrayList(items));
            this.comboBox.setEditable(editable);
            getChildren().addAll(this.label, this.comboBox);
            if (required) {
                getChildren().add(createAsterisk());
            }
        }

        public ComboBox<T> getComboBox() {
            return comboBox;
        }

        public Label getLabel() {
            return label;
        }
    }

    public static class Data<T> {
        private final String label;
        private List<T> items;
        private boolean editable = true;
        private boolean required = false;

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
    }
}
