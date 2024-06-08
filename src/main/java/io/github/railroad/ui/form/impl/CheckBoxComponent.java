package io.github.railroad.ui.form.impl;

import io.github.railroad.ui.form.FormComponent;
import io.github.railroad.ui.form.FormComponentChangeListener;
import io.github.railroad.ui.form.FormComponentValidator;
import io.github.railroad.ui.form.ui.FormCheckBox;
import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.CheckBox;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

public class CheckBoxComponent extends FormComponent<FormCheckBox, CheckBoxComponent.Data, CheckBox, Boolean> {
    public CheckBoxComponent(Data data, FormComponentValidator<CheckBox> validator, FormComponentChangeListener<CheckBox, Boolean> listener, Property<CheckBox> bindCheckboxTo) {
        super(data, dataCurrent -> new FormCheckBox(dataCurrent.label, dataCurrent.selected, dataCurrent.required), validator, listener);

        if(bindCheckboxTo != null) {
            bindCheckboxTo.bind(componentProperty().map(FormCheckBox::getPrimaryComponent));
        }
    }

    @Override
    public ObservableValue<CheckBox> getValidationNode() {
        return componentProperty().map(FormCheckBox::getPrimaryComponent);
    }

    @Override
    protected void applyListener(FormComponentChangeListener<CheckBox, Boolean> listener) {
        AtomicReference<ChangeListener<Boolean>> listenerRef = new AtomicReference<>();
        componentProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.getPrimaryComponent().selectedProperty().removeListener(listenerRef.get());
            }

            if (newValue != null) {
                listenerRef.set((observable1, oldValue1, newValue1) ->
                        listener.changed(newValue.getPrimaryComponent(), observable1, oldValue1, newValue1));

                newValue.getPrimaryComponent().selectedProperty().addListener(listenerRef.get());
            }
        });
    }

    public static class Builder {
        private final Data data;
        private FormComponentValidator<CheckBox> validator;
        private FormComponentChangeListener<CheckBox, Boolean> listener;
        private Property<CheckBox> bindCheckboxTo;

        public Builder(@NotNull String label) {
            this.data = new Data(label);
        }

        public Builder selected(boolean selected) {
            this.data.selected(selected);
            return this;
        }

        public Builder required(boolean required) {
            this.data.required(required);
            return this;
        }

        public Builder validator(FormComponentValidator<CheckBox> validator) {
            this.validator = validator;
            return this;
        }

        public Builder listener(FormComponentChangeListener<CheckBox, Boolean> listener) {
            this.listener = listener;
            return this;
        }

        public Builder bindCheckboxTo(Property<CheckBox> bindCheckboxTo) {
            this.bindCheckboxTo = bindCheckboxTo;
            return this;
        }

        public CheckBoxComponent build() {
            return new CheckBoxComponent(data, validator, listener, bindCheckboxTo);
        }
    }

    public static class Data {
        private final String label;
        private boolean selected = false;
        private boolean required = false;

        public Data(@NotNull String label) {
            this.label = label;
        }

        public Data selected(boolean selected) {
            this.selected = selected;
            return this;
        }

        public Data required(boolean required) {
            this.required = required;
            return this;
        }
    }
}
