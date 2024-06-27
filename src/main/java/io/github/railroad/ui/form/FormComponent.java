package io.github.railroad.ui.form;

import io.github.railroad.ui.form.impl.*;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

public abstract class FormComponent<T extends Node, U, V extends Node, W> {
    private final UpdatableObjectProperty<U> data = new UpdatableObjectProperty<>();
    private final UpdatableObjectProperty<T> component = new UpdatableObjectProperty<>();
    private final FormComponentChangeListener<V, W> listener;
    private final FormComponentValidator<V> validator;
    private final BooleanBinding visible;

    public FormComponent(U data, Function<U, T> componentFactory, FormComponentValidator<V> validator, FormComponentChangeListener<V, W> listener, List<FormTransformer<V, W, ?>> transformers, @Nullable BooleanBinding visible, Runnable setup) {
        this.validator = validator;
        this.listener = (node, observable, oldValue, newValue) -> {
            runValidation(node);

            transformers.forEach(FormTransformer::transform);

            if (listener != null) {
                listener.changed(node, observable, oldValue, newValue);
            }
        };

        this.visible = visible;

        applyListener(this.listener);
        setup.run();

        this.data.set(data);
        this.component.set(componentFactory.apply(this.data.get()));
        if(this.visible != null)
            this.component.get().visibleProperty().bind(this.visible);

        this.data.addListener((observable, oldValue, newValue) ->
                component.set(componentFactory.apply(newValue)));
        this.component.addListener((observable, oldValue, newValue) -> {
            this.data.set(newValue == null ? null : this.data.get());
            if (this.visible != null)
                this.component.get().visibleProperty().bind(this.visible);
        });
    }

    public FormComponent(U data, Function<U, T> componentFactory, FormComponentValidator<V> validator, FormComponentChangeListener<V, W> listener, List<FormTransformer<V, W, ?>> transformers, @Nullable BooleanBinding visible) {
        this(data, componentFactory, validator, listener, transformers, visible, () -> {});
    }

    public UpdatableObjectProperty<U> dataProperty() {
        return data;
    }

    public U getData() {
        return data.get();
    }

    public void setData(U data) {
        this.data.set(data);
    }

    public UpdatableObjectProperty<T> componentProperty() {
        return component;
    }

    public T getComponent() {
        return component.get();
    }

    public abstract ObservableValue<V> getValidationNode();

    protected abstract void applyListener(FormComponentChangeListener<V, W> listener);

    public ValidationResult validate() {
        return validator.apply(getValidationNode().getValue());
    }

    public void reset() {
        data.set(data.get());
    }

    public void disable(boolean disable) {
        component.get().setDisable(disable);
    }

    protected void runValidation(V node) {
        if(validator == null)
            return;

        ValidationResult result = validator.apply(node);
        if (result.status() == ValidationResult.Status.OK) {
            node.getStyleClass().removeAll("warning", "error");
        } else if (result.status() == ValidationResult.Status.WARNING) {
            node.getStyleClass().remove("error");

            if(!node.getStyleClass().contains("warning")) {
                node.getStyleClass().add("warning");
            }
        } else {
            node.getStyleClass().remove("warning");

            if(!node.getStyleClass().contains("error")) {
                node.getStyleClass().add("error");
            }
        }

        if(result.message() != null) {
            Tooltip.install(node, new Tooltip(result.message()));
        } else {
            Tooltip.uninstall(node, null);
        }
    }

    public static TextFieldComponent.Builder textField(String label) {
        return new TextFieldComponent.Builder(label);
    }

    public static <T> ComboBoxComponent.Builder<T> comboBox(String label, Class<T> ignored) {
        return new ComboBoxComponent.Builder<>(label);
    }

    public static CheckBoxComponent.Builder checkBox(String label) {
        return new CheckBoxComponent.Builder(label);
    }

    public static DirectoryChooserComponent.Builder directoryChooser(String label) {
        return new DirectoryChooserComponent.Builder(label);
    }

    public static TextAreaComponent.Builder textArea(String label) {
        return new TextAreaComponent.Builder(label);
    }
}
