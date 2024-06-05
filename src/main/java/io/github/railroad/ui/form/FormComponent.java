package io.github.railroad.ui.form;

import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.form.impl.ComboBoxComponent;
import io.github.railroad.ui.form.impl.TextFieldComponent;
import io.github.railroad.utility.FromStringFunction;
import io.github.railroad.utility.ToStringFunction;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class FormComponent<T extends Node, U> {
    private final ObjectProperty<U> data = new SimpleObjectProperty<>();
    private final ObjectProperty<T> component = new SimpleObjectProperty<>();

    public FormComponent(Supplier<U> dataFactory, Function<U, T> componentFactory) {
        this.data.set(dataFactory.get());
        this.component.set(componentFactory.apply(this.data.get()));
        this.data.addListener((observable, oldValue, newValue) ->
                component.set(componentFactory.apply(newValue)));

        component.addListener((observable, oldValue, newValue) ->
                data.set(newValue == null ? null : dataFactory.get()));
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

    protected static Label createLabel(RRHBox hBox, String label, boolean required) {
        var labelNode = new Label(label);
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

    public static TextFieldComponent textField(String label, String text, String promptText, boolean editable, boolean required) {
        return new TextFieldComponent(() -> new TextFieldComponent.Data(label).text(text).promptText(promptText).editable(editable).required(required));
    }

    public static TextFieldComponent textField(String label, boolean required) {
        return new TextFieldComponent(() -> new TextFieldComponent.Data(label).required(required));
    }

    public static TextFieldComponent textField(String label) {
        return new TextFieldComponent(() -> new TextFieldComponent.Data(label));
    }

    public static <T> ComboBoxComponent<T> comboBox(String label, ToStringFunction<T> toStringFunction, FromStringFunction<T> fromStringFunction) {
        return comboBox(() -> new ComboBoxComponent.Data<>(label), toStringFunction, fromStringFunction);
    }

    public static <T> ComboBoxComponent<T> comboBox(Supplier<ComboBoxComponent.Data<T>> dataSupplier, ToStringFunction<T> toStringFunction, FromStringFunction<T> fromStringFunction) {
        return new ComboBoxComponent<>(dataSupplier, toStringFunction, fromStringFunction);
    }

    public static <T> ComboBoxComponent<T> comboBox(String label, Collection<T> items, boolean editable, boolean required, ToStringFunction<T> toStringFunction, FromStringFunction<T> fromStringFunction) {
        return comboBox(() -> new ComboBoxComponent.Data<T>(label).items(items).editable(editable).required(required), toStringFunction, fromStringFunction);
    }

    public static <T> ComboBoxComponent<T> comboBox(String label, Collection<T> items, boolean editable, boolean required) {
        return new ComboBoxComponent<>(() -> new ComboBoxComponent.Data<T>(label).items(items).editable(editable).required(required));
    }

    public static <T> ComboBoxComponent<T> comboBox(String label, Collection<T> items) {
        return new ComboBoxComponent<>(() -> new ComboBoxComponent.Data<T>(label).items(items));
    }

    public static <T> ComboBoxComponent<T> comboBox(String label, Collection<T> items, ToStringFunction<T> toStringFunction, FromStringFunction<T> fromStringFunction) {
        return comboBox(() -> new ComboBoxComponent.Data<T>(label).items(items), toStringFunction, fromStringFunction);
    }

    public static <T> ComboBoxComponent<T> comboBox(String label) {
        return new ComboBoxComponent<>(() -> new ComboBoxComponent.Data<>(label));
    }
}
