package dev.railroadide.railroad.ide.runconfig.ui.form;

import dev.railroadide.railroad.form.*;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Form component wrapping {@link FormRunConfigurationPicker}.
 */
public class RunConfigurationPickerComponent
    extends
        FormComponent<FormRunConfigurationPicker, RunConfigurationPickerComponent.Data, FormRunConfigurationPicker, RunConfiguration<?>[]> {
    private final Supplier<List<RunConfiguration<?>>> initialSelectionSupplier;

    /**
     * Creates a picker form component and connects its validation, change listener, and visibility binding.
     *
     * @param dataKey the form-data key receiving the selected configuration array
     * @param data the picker options, including its initial selection supplier
     * @param validator the picker validator
     * @param listener the optional selection-change listener
     * @param visible the optional visibility binding
     */
    public RunConfigurationPickerComponent(
        String dataKey,
        Data data,
        FormComponentValidator<FormRunConfigurationPicker> validator,
        FormComponentChangeListener<FormRunConfigurationPicker, RunConfiguration<?>[]> listener,
        BooleanBinding visible
    ) {
        super(dataKey,
            data,
            currentData -> new FormRunConfigurationPicker(
                currentData.labelKey,
                currentData.required,
                currentData.availableConfigurations,
                currentData.filter,
                currentData.initialSelectionSupplier.get()),
            validator,
            listener,
            List.of(),
            visible);
        this.initialSelectionSupplier = data.initialSelectionSupplier;
    }

    @Override
    public ObservableValue<FormRunConfigurationPicker> getValidationNode() {
        return componentProperty();
    }

    @Override
    protected void applyListener(
        FormComponentChangeListener<FormRunConfigurationPicker, RunConfiguration<?>[]> listener
    ) {
        AtomicReference<ChangeListener<RunConfiguration<?>[]>> listenerRef = new AtomicReference<>();
        componentProperty().addListener((_, oldValue, newValue) -> {
            if (oldValue != null && listenerRef.get() != null) {
                oldValue.valueProperty().removeListener(listenerRef.get());
            }

            if (newValue != null) {
                ChangeListener<RunConfiguration<?>[]> changeListener = (obs, oldArr, newArr) -> listener
                    .changed(newValue, obs, oldArr, newArr);
                listenerRef.set(changeListener);
                newValue.valueProperty().addListener(changeListener);
            }
        });
    }

    @Override
    protected void bindToFormData(FormData formData) {
        componentProperty().addListener((_, _, newValue) -> {
            if (newValue != null) {
                registerBinding(newValue, formData);
            }
        });

        if (componentProperty().get() != null) {
            registerBinding(componentProperty().get(), formData);
        }
    }

    @Override
    public void reset() {
        FormRunConfigurationPicker picker = componentProperty().get();
        if (picker != null) {
            List<RunConfiguration<?>> defaults = initialSelectionSupplier.get();
            picker.setValue(defaults == null ? new RunConfiguration<?>[0] : defaults.toArray(new RunConfiguration[0]));
        }
    }

    private void registerBinding(FormRunConfigurationPicker picker, FormData formData) {
        picker.valueProperty().addListener(
            (_, _, newValue) -> formData.add(dataKey, newValue == null ? new RunConfiguration[0] : newValue));
        formData.add(dataKey, picker.getValue());
    }

    /**
     * Creates a builder for a run configuration picker form field.
     *
     * @param dataKey the form-data key receiving the selected configuration array
     * @return a new picker component builder
     */
    public static Builder builder(String dataKey) {
        return new Builder(dataKey);
    }

    /**
     * Builds a run configuration picker with configurable choices, validation, and initial selection.
     */
    public static class Builder
        implements
            FormComponentBuilder<RunConfigurationPickerComponent, FormRunConfigurationPicker, RunConfiguration<?>[], Builder> {
        private final String dataKey;
        private final Data data = new Data();
        private FormComponentValidator<FormRunConfigurationPicker> validator;
        private FormComponentChangeListener<FormRunConfigurationPicker, RunConfiguration<?>[]> listener;
        private BooleanBinding visible;

        private Builder(String dataKey) {
            this.dataKey = Objects.requireNonNull(dataKey);
            data.initialSelectionSupplier = ArrayList::new;
            data.filter = Objects::nonNull;
            data.required = true;
        }

        /**
         * Sets the translation key for the picker label.
         *
         * @param labelKey the label localization key
         * @return this builder
         */
        public Builder labelKey(String labelKey) {
            data.labelKey = labelKey;
            return this;
        }

        /**
         * Sets whether the picker requires at least one selection under its default validator.
         *
         * @param required whether a selection is required
         * @return this builder
         */
        public Builder required(boolean required) {
            data.required = required;
            return this;
        }

        /**
         * Sets the observable collection from which users may select configurations.
         *
         * @param configurations the available run configurations
         * @return this builder
         */
        public Builder availableConfigurations(ObservableList<RunConfiguration<?>> configurations) {
            data.availableConfigurations = configurations;
            return this;
        }

        /**
         * Sets the predicate controlling which configurations may be added to the selection.
         *
         * @param predicate the eligibility predicate, or {@code null} to allow nonnull configurations
         * @return this builder
         */
        public Builder filter(Predicate<RunConfiguration<?>> predicate) {
            data.filter = predicate;
            return this;
        }

        /**
         * Sets the supplier queried when the picker is created or reset.
         *
         * @param supplier the supplier of ordered initial selections
         * @return this builder
         */
        public Builder initialSelectionSupplier(Supplier<List<RunConfiguration<?>>> supplier) {
            data.initialSelectionSupplier = supplier;
            return this;
        }

        @Override
        public Builder validator(FormComponentValidator<FormRunConfigurationPicker> validator) {
            this.validator = validator;
            return this;
        }

        @Override
        public Builder listener(
            FormComponentChangeListener<FormRunConfigurationPicker, RunConfiguration<?>[]> listener
        ) {
            this.listener = listener;
            return this;
        }

        @Override
        public <X> Builder addTransformer(
            ObservableValue<FormRunConfigurationPicker> fromComponent,
            Consumer<X> toComponentFunction,
            Function<RunConfiguration<?>[], X> valueMapper
        ) {
            throw new UnsupportedOperationException(
                "Transformers are not supported for RunConfigurationPickerComponent.");
        }

        @Override
        public <U extends Node, X> Builder addTransformer(
            ObservableValue<FormRunConfigurationPicker> fromComponent,
            ObservableValue<U> toComponent,
            Function<RunConfiguration<?>[], X> valueMapper
        ) {
            throw new UnsupportedOperationException(
                "Transformers are not supported for RunConfigurationPickerComponent.");
        }

        @Override
        public Builder visible(BooleanBinding visible) {
            this.visible = visible;
            return this;
        }

        @Override
        public String dataKey() {
            return dataKey;
        }

        @Override
        public RunConfigurationPickerComponent build() {
            if (data.availableConfigurations == null)
                throw new IllegalStateException("availableConfigurations must be set");

            if (data.labelKey == null)
                throw new IllegalStateException("labelKey must be set");

            FormComponentValidator<FormRunConfigurationPicker> validatorToUse = validator != null
                ? validator
                : picker -> {
                    if (data.required && picker.getSelectedConfigurations().isEmpty())
                        return ValidationResult
                            .error("railroad.runconfig.compound.configuration.configurations.validation.required");

                    return ValidationResult.ok();
                };

            return new RunConfigurationPickerComponent(dataKey, data, validatorToUse, listener, visible);
        }
    }

    /**
     * Stores the label, selection requirements, available choices, filter, and reset selection for a picker.
     */
    public static class Data {
        private String labelKey;
        private boolean required;
        private ObservableList<RunConfiguration<?>> availableConfigurations;
        private Predicate<RunConfiguration<?>> filter;
        private Supplier<List<RunConfiguration<?>>> initialSelectionSupplier;
    }
}
