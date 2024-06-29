package io.github.railroad.ui.form;

import io.github.railroad.Railroad;
import io.github.railroad.ui.form.impl.*;
import io.github.railroad.utility.localization.L18n;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

public abstract class FormComponent<T extends Node, U, V extends Node, W> {
    protected final String dataKey;
    private final UpdatableObjectProperty<U> data = new UpdatableObjectProperty<>();
    private final UpdatableObjectProperty<T> component = new UpdatableObjectProperty<>();
    private final FormComponentChangeListener<V, W> listener;
    private final FormComponentValidator<V> validator;
    private final BooleanBinding visible;

    public FormComponent(String dataKey, U data, Function<U, T> componentFactory, FormComponentValidator<V> validator, FormComponentChangeListener<V, W> listener, List<FormTransformer<V, W, ?>> transformers, @Nullable BooleanBinding visible, Runnable setup) {
        this.dataKey = dataKey;
        this.validator = node -> {
            if(validator == null || node == null || !isVisible(node))
                return ValidationResult.ok();

            return validator.apply(node);
        };

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

    private static boolean isVisible(Node node) {
        return node.isVisible() && (node.getParent() == null || isVisible(node.getParent()));
    }

    public FormComponent(String dataKey, U data, Function<U, T> componentFactory, FormComponentValidator<V> validator, FormComponentChangeListener<V, W> listener, List<FormTransformer<V, W, ?>> transformers, @Nullable BooleanBinding visible) {
        this(dataKey, data, componentFactory, validator, listener, transformers, visible, () -> {});
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

    protected abstract void bindToFormData(FormData formData);

    public ValidationResult validate() {
        return validator.apply(getValidationNode().getValue());
    }

    public void reset() {
        data.set(data.get());
    }

    public void disable(boolean disable) {
        component.get().setDisable(disable);
    }

    // TODO: Toast messages for validation errors
    protected void runValidation(V node) {
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
            String message = L18n.localize(result.message());
            Tooltip.install(node, new Tooltip(message));

            Railroad.LOGGER.warn("Validation error: {}", message);
        } else {
            Tooltip.uninstall(node, null);
        }
    }

    protected void runValidation() {
        runValidation(getValidationNode().getValue());
    }

    public static TextFieldComponent.Builder textField(@NotNull String dataKey, @NotNull String label) {
        return new TextFieldComponent.Builder(dataKey, label);
    }

    public static <T> ComboBoxComponent.Builder<T> comboBox(@NotNull String dataKey, @NotNull String label, @NotNull Class<T> ignored) {
        return new ComboBoxComponent.Builder<>(dataKey, label);
    }

    public static CheckBoxComponent.Builder checkBox(@NotNull String dataKey, @NotNull String label) {
        return new CheckBoxComponent.Builder(dataKey, label);
    }

    public static DirectoryChooserComponent.Builder directoryChooser(@NotNull String dataKey, @NotNull String label) {
        return new DirectoryChooserComponent.Builder(dataKey, label);
    }

    public static TextAreaComponent.Builder textArea(@NotNull String dataKey, @NotNull String label) {
        return new TextAreaComponent.Builder(dataKey, label);
    }
}
