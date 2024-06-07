package io.github.railroad.ui.form;

import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.form.impl.CheckboxComponent;
import io.github.railroad.ui.form.impl.ComboBoxComponent;
import io.github.railroad.ui.form.impl.DirectoryChooserComponent;
import io.github.railroad.ui.form.impl.TextFieldComponent;
import io.github.railroad.ui.localized.LocalizedLabel;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

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

    protected static LocalizedLabel createLabel(RRHBox hBox, String label, boolean required) {
        var labelNode = new LocalizedLabel(label);
        hBox.getChildren().add(labelNode);
        if (required) {
            hBox.getChildren().add(createAsterisk());
        }

        return labelNode;
    }

    protected static Text createAsterisk() {
        var asterisk = new Text("*");
        asterisk.setFill(Color.RED);
        Tooltip.install(asterisk, new Tooltip("Required"));
        return asterisk;
    }

    public static TextFieldComponent textField(TextFieldComponent.Data data, FormComponentValidator<TextField> validator, FormComponentChangeListener<TextField, String> listener) {
        return new TextFieldComponent(data, validator, listener);
    }

    public static <T> ComboBoxComponent<T> comboBox(ComboBoxComponent.Data<T> data, FormComponentValidator<ComboBox<T>> validator, FormComponentChangeListener<ComboBox<T>, T> listener) {
        return new ComboBoxComponent<>(data, validator, listener);
    }

    public static CheckboxComponent checkbox(CheckboxComponent.Data data, FormComponentValidator<javafx.scene.control.CheckBox> validator, FormComponentChangeListener<javafx.scene.control.CheckBox, Boolean> listener) {
        return new CheckboxComponent(data, validator, listener);
    }

    public static DirectoryChooserComponent directoryChooser(DirectoryChooserComponent.Data data, FormComponentValidator<TextField> validator, FormComponentChangeListener<TextField, String> listener) {
        return new DirectoryChooserComponent(data, validator, listener);
    }
}
