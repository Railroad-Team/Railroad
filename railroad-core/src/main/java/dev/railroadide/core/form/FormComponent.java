package dev.railroadide.core.form;

import dev.railroadide.core.form.impl.*;
import dev.railroadide.core.localization.LocalizationServiceLocator;
import dev.railroadide.core.logger.LoggerServiceLocator;
import dev.railroadide.core.ui.localized.LocalizedTooltip;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * Represents an abstract form component.
 *
 * @param <T> The JavaFX node type of the component.
 * @param <U> The data type of the component.
 * @param <V> The JavaFX node type of the validation node.
 * @param <W> The data type of the validation node.
 */
public abstract class FormComponent<T extends Node, U, V extends Node, W> {
    protected final String dataKey;
    private final UpdatableObjectProperty<U> data = new UpdatableObjectProperty<>();
    private final UpdatableObjectProperty<T> component = new UpdatableObjectProperty<>();
    private final FormComponentChangeListener<V, W> listener;
    private final FormComponentValidator<V> validator;
    private final BooleanBinding visible;

    /**
     * Creates a new form component.
     *
     * @param dataKey          The key of the data.
     * @param data             The data.
     * @param componentFactory The factory for creating the component.
     * @param validator        The validator for the component.
     * @param listener         The listener for the component.
     * @param transformers     The transformers for the component.
     * @param visible          The visibility of the component.
     * @param setup            The setup for the component.
     * @apiNote This constructor is internal and should not be used, only override it.
     */
    @ApiStatus.Internal
    protected FormComponent(String dataKey, U data, Function<U, T> componentFactory, FormComponentValidator<V> validator, FormComponentChangeListener<V, W> listener, List<FormTransformer<V, W, ?>> transformers, @Nullable BooleanBinding visible, Runnable setup) {
        this.dataKey = dataKey;
        this.validator = node -> {
            if (validator == null || node == null || !isVisible(node))
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
        if (this.visible != null)
            this.component.get().visibleProperty().bind(this.visible);

        this.data.addListener((observable, oldValue, newValue) ->
                component.set(componentFactory.apply(newValue)));
        this.component.addListener((observable, oldValue, newValue) -> {
            this.data.set(newValue == null ? null : this.data.get());
            if (this.visible != null)
                this.component.get().visibleProperty().bind(this.visible);
        });
    }

    /**
     * Creates a new form component.
     *
     * @param dataKey          The key of the data.
     * @param data             The data.
     * @param componentFactory The factory for creating the component.
     * @param validator        The validator for the component.
     * @param listener         The listener for the component.
     * @param transformers     The transformers for the component.
     * @param visible          The visibility of the component.
     * @implNote This constructor is internal and should not be used, only override it.
     */
    @ApiStatus.Internal
    protected FormComponent(String dataKey, U data, Function<U, T> componentFactory, FormComponentValidator<V> validator, FormComponentChangeListener<V, W> listener, List<FormTransformer<V, W, ?>> transformers, @Nullable BooleanBinding visible) {
        this(dataKey, data, componentFactory, validator, listener, transformers, visible, () -> {
        });
    }

    /**
     * Checks if a node is visible.
     *
     * @param node The node to check.
     * @return Whether the node is visible.
     * @implNote This method will traverse the parent hierarchy to check if the node is visible.
     */
    private static boolean isVisible(Node node) {
        return node.isVisible() && (node.getParent() == null || isVisible(node.getParent()));
    }

    /**
     * Creates a new text field component.
     *
     * @param dataKey The key of the data.
     * @param label   The label of the component.
     * @return The builder for the text field component.
     */
    public static TextFieldComponent.Builder textField(@NotNull String dataKey, @NotNull String label) {
        return new TextFieldComponent.Builder(dataKey, label);
    }

    /**
     * Creates a new combo box component.
     *
     * @param dataKey   The key of the data.
     * @param label     The label of the component.
     * @param itemClazz The class of the items in the combo box.
     * @apiNote The {@param itemClazz} is used to determine the type of the items in the combo box,
     * however, it is not actually used in the implementation.
     */
    public static <T> ComboBoxComponent.Builder<T> comboBox(@NotNull String dataKey, @NotNull String label, @NotNull Class<T> itemClazz) {
        return new ComboBoxComponent.Builder<>(dataKey, label);
    }

    /**
     * Creates a new checkbox component.
     *
     * @param dataKey The key of the data.
     * @param label   The label of the component.
     * @return The builder for the checkbox component.
     */
    public static CheckBoxComponent.Builder checkBox(@NotNull String dataKey, @NotNull String label) {
        return new CheckBoxComponent.Builder(dataKey, label);
    }

    /**
     * Creates a new directory chooser component.
     *
     * @param dataKey The key of the data.
     * @param label   The label of the component.
     * @return The builder for the directory chooser component.
     */
    public static DirectoryChooserComponent.Builder directoryChooser(@NotNull String dataKey, @NotNull String label) {
        return new DirectoryChooserComponent.Builder(dataKey, label);
    }

    /**
     * Creates a new text area component.
     *
     * @param dataKey The key of the data.
     * @param label   The label of the component.
     * @return The builder for the text area component.
     */
    public static TextAreaComponent.Builder textArea(@NotNull String dataKey, @NotNull String label) {
        return new TextAreaComponent.Builder(dataKey, label);
    }

    /**
     * Returns a property that represents the default data of the component.
     *
     * @return The data property.
     */
    public UpdatableObjectProperty<U> dataProperty() {
        return data;
    }

    /**
     * Returns the data of the component.
     *
     * @return The data.
     */
    public U getData() {
        return data.get();
    }

    /**
     * Sets the data of the component.
     *
     * @param data The data.
     */
    public void setData(U data) {
        this.data.set(data);
    }

    /**
     * Returns a property that represents the component of the component.
     *
     * @return The component property.
     */
    public UpdatableObjectProperty<T> componentProperty() {
        return component;
    }

    /**
     * Returns the component of the component.
     *
     * @return The component.
     */
    public T getComponent() {
        return component.get();
    }

    /**
     * Gets the validation node of the component as an @{@link ObservableValue}.
     *
     * @return The validation node.
     */
    public abstract ObservableValue<V> getValidationNode();

    /**
     * Applies a listener to the component.
     *
     * @param listener The listener to apply.
     */
    protected abstract void applyListener(FormComponentChangeListener<V, W> listener);

    /**
     * Binds the component to a form data.
     *
     * @param formData The form data to bind to.
     */
    protected abstract void bindToFormData(FormData formData);

    /**
     * Resets the component.
     */
    public abstract void reset();

    /**
     * Validates the component.
     *
     * @return The validation result.
     */
    public ValidationResult validate() {
        return validator.apply(getValidationNode().getValue());
    }

    /**
     * Disables the component.
     *
     * @param disable Whether to disable the component.
     */
    public void disable(boolean disable) {
        component.get().setDisable(disable);
    }

    /**
     * Runs the validation for the component.
     *
     * @param node The node to validate.
     */
    // TODO: Toast messages for validation errors
    protected void runValidation(V node) {
        ValidationResult result = validator.apply(node);
        if (result.status() == ValidationResult.Status.OK) {
            node.getStyleClass().removeAll("warning", "error");
        } else if (result.status() == ValidationResult.Status.WARNING) {
            node.getStyleClass().remove("error");

            if (!node.getStyleClass().contains("warning")) {
                node.getStyleClass().add("warning");
            }
        } else {
            node.getStyleClass().remove("warning");

            if (!node.getStyleClass().contains("error")) {
                node.getStyleClass().add("error");
            }
        }

        if (result.message() != null) {
            String message = result.message();
            Tooltip.install(node, new LocalizedTooltip(message));

            LoggerServiceLocator.getInstance().getLogger().warn("Validation error: {}", LocalizationServiceLocator.getInstance().get(message));
        } else {
            Tooltip.uninstall(node, null);
        }
    }

    /**
     * Runs the validation for the component.
     * <p>
     * This method will run the validation for the current validation node.
     * </p>
     *
     * @implNote This method is internal and should not be used, only override it.
     */
    protected void runValidation() {
        runValidation(getValidationNode().getValue());
    }
}
