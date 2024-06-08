package io.github.railroad.ui.form;

import io.github.railroad.ui.form.impl.CheckBoxComponent;
import io.github.railroad.ui.form.impl.ComboBoxComponent;
import io.github.railroad.ui.form.impl.DirectoryChooserComponent;
import io.github.railroad.ui.form.impl.TextFieldComponent;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;

import java.util.function.Function;

public abstract class FormComponent<T extends Node, U, V extends Node, W> {
    private final ObjectProperty<U> data = new SimpleObjectProperty<>();
    private final ObjectProperty<T> component = new SimpleObjectProperty<>();
    private final FormComponentValidator<V> validator;

    public FormComponent(U data, Function<U, T> componentFactory, FormComponentValidator<V> validator, FormComponentChangeListener<V, W> listener) {
        this.data.set(data);
        this.component.set(componentFactory.apply(this.data.get()));

        this.validator = validator;

        this.data.addListener((observable, oldValue, newValue) ->
                component.set(componentFactory.apply(newValue)));
        this.component.addListener((observable, oldValue, newValue) ->
                this.data.set(newValue == null ? null : this.data.get()));

        applyListener(listener);
    }

    public ObjectProperty<U> dataProperty() {
        return data;
    }

    public U getData() {
        return data.get();
    }

    public void setData(U data) {
        this.data.set(data);
    }

    public ObjectProperty<T> componentProperty() {
        return component;
    }

    public T getComponent() {
        return component.get();
    }

    public abstract ObservableValue<V> getValidationNode();

    public boolean isValid() {
        return validator.validate(getValidationNode().getValue());
    }

    protected abstract void applyListener(FormComponentChangeListener<V, W> listener);

    public void reset() {
        data.set(data.get());
    }

    public void disable(boolean disable) {
        component.get().setDisable(disable);
    }

    public static TextFieldComponent.Builder textField(String label) {
        return new TextFieldComponent.Builder(label);
    }

    public static ComboBoxComponent.Builder<?> comboBox(String label) {
        return new ComboBoxComponent.Builder<>(label);
    }

    public static CheckBoxComponent.Builder checkBox(String label) {
        return new CheckBoxComponent.Builder(label);
    }

    public static DirectoryChooserComponent.Builder directoryChooser(String label) {
        return new DirectoryChooserComponent.Builder(label);
    }
}
