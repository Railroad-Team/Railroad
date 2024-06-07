package io.github.railroad.ui.form.impl;

import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.form.FormComponent;
import io.github.railroad.ui.form.FormComponentChangeListener;
import io.github.railroad.ui.form.FormComponentValidator;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

public class CheckboxComponent extends FormComponent<CheckboxComponent.FormCheckbox, CheckboxComponent.Data, CheckBox, Boolean> {
    public CheckboxComponent(Data data, FormComponentValidator<CheckBox> validator, FormComponentChangeListener<CheckBox, Boolean> listener) {
        super(data, dataCurrent -> new FormCheckbox(dataCurrent.label, dataCurrent.selected, dataCurrent.required), validator, listener);
    }

    @Override
    public ObservableValue<CheckBox> getValidationNode() {
        return componentProperty().map(CheckboxComponent.FormCheckbox::getCheckBox);
    }

    @Override
    protected void applyListener(FormComponentChangeListener<CheckBox, Boolean> listener) {
        AtomicReference<ChangeListener<Boolean>> listenerRef = new AtomicReference<>();
        componentProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.checkBox.selectedProperty().removeListener(listenerRef.get());
            }

            if (newValue != null) {
                listenerRef.set((observable1, oldValue1, newValue1) ->
                        listener.changed(newValue.checkBox, observable1, oldValue1, newValue1));

                newValue.checkBox.selectedProperty().addListener(listenerRef.get());
            }
        });
    }


    @Getter
    public static class FormCheckbox extends RRHBox {
        private final CheckBox checkBox;
        private final Label label;

        public FormCheckbox(@NotNull String label, boolean selected, boolean required) {
            setSpacing(10);

            this.label = createLabel(this, label, required);

            this.checkBox = new CheckBox();
            this.checkBox.setSelected(selected);
            getChildren().add(this.checkBox);
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
