package dev.railroadide.railroad.ide.runconfig.ui.form;

import dev.railroadide.core.form.*;
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
public class RunConfigurationPickerComponent extends FormComponent<FormRunConfigurationPicker, RunConfigurationPickerComponent.Data, FormRunConfigurationPicker, RunConfiguration<?>[]> {
    private final Supplier<List<RunConfiguration<?>>> initialSelectionSupplier;

    public RunConfigurationPickerComponent(String dataKey,
                                           Data data,
                                           FormComponentValidator<FormRunConfigurationPicker> validator,
                                           FormComponentChangeListener<FormRunConfigurationPicker, RunConfiguration<?>[]> listener,
                                           BooleanBinding visible) {
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
    protected void applyListener(FormComponentChangeListener<FormRunConfigurationPicker, RunConfiguration<?>[]> listener) {
        AtomicReference<ChangeListener<RunConfiguration<?>[]>> listenerRef = new AtomicReference<>();
        componentProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null && listenerRef.get() != null) {
                oldValue.valueProperty().removeListener(listenerRef.get());
            }

            if (newValue != null) {
                ChangeListener<RunConfiguration<?>[]> changeListener = (obs, oldArr, newArr) ->
                    listener.changed(newValue, obs, oldArr, newArr);
                listenerRef.set(changeListener);
                newValue.valueProperty().addListener(changeListener);
            }
        });
    }

    @Override
    protected void bindToFormData(FormData formData) {
        componentProperty().addListener((observable, oldValue, newValue) -> {
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
        picker.valueProperty().addListener((observable, oldValue, newValue) ->
            formData.add(dataKey, newValue == null ? new RunConfiguration[0] : newValue));
        formData.add(dataKey, picker.getValue());
    }

    public static Builder builder(String dataKey) {
        return new Builder(dataKey);
    }

    public static class Builder implements FormComponentBuilder<RunConfigurationPickerComponent, FormRunConfigurationPicker, RunConfiguration<?>[], Builder> {
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

        public Builder labelKey(String labelKey) {
            data.labelKey = labelKey;
            return this;
        }

        public Builder required(boolean required) {
            data.required = required;
            return this;
        }

        public Builder availableConfigurations(ObservableList<RunConfiguration<?>> configurations) {
            data.availableConfigurations = configurations;
            return this;
        }

        public Builder filter(Predicate<RunConfiguration<?>> predicate) {
            data.filter = predicate;
            return this;
        }

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
        public Builder listener(FormComponentChangeListener<FormRunConfigurationPicker, RunConfiguration<?>[]> listener) {
            this.listener = listener;
            return this;
        }

        @Override
        public <X> Builder addTransformer(ObservableValue<FormRunConfigurationPicker> fromComponent, Consumer<X> toComponentFunction, Function<RunConfiguration<?>[], X> valueMapper) {
            throw new UnsupportedOperationException("Transformers are not supported for RunConfigurationPickerComponent.");
        }

        @Override
        public <U extends Node, X> Builder addTransformer(ObservableValue<FormRunConfigurationPicker> fromComponent, ObservableValue<U> toComponent, Function<RunConfiguration<?>[], X> valueMapper) {
            throw new UnsupportedOperationException("Transformers are not supported for RunConfigurationPickerComponent.");
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

            FormComponentValidator<FormRunConfigurationPicker> validatorToUse = validator != null ? validator : picker -> {
                if (data.required && picker.getSelectedConfigurations().isEmpty())
                    return ValidationResult.error("railroad.runconfig.compound.configuration.configurations.validation.required");

                return ValidationResult.ok();
            };

            return new RunConfigurationPickerComponent(dataKey, data, validatorToUse, listener, visible);
        }
    }

    public static class Data {
        private String labelKey;
        private boolean required;
        private ObservableList<RunConfiguration<?>> availableConfigurations;
        private Predicate<RunConfiguration<?>> filter;
        private Supplier<List<RunConfiguration<?>>> initialSelectionSupplier;
    }
}
