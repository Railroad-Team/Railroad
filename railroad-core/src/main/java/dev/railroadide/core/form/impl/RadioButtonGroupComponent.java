package dev.railroadide.core.form.impl;

import dev.railroadide.core.form.*;
import dev.railroadide.core.form.ui.FormRadioButtonGroup;
import dev.railroadide.core.utility.ServiceLocator;
import dev.railroadide.logger.Logger;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Form component that renders an enum as a radio button group.
 *
 * @param <E> enum type
 */
public class RadioButtonGroupComponent<E extends Enum<E>> extends FormComponent<FormRadioButtonGroup<E>, RadioButtonGroupComponent.Data<E>, FormRadioButtonGroup<E>, E> {
    public RadioButtonGroupComponent(String dataKey,
                                     Data<E> data,
                                     FormComponentValidator<FormRadioButtonGroup<E>> validator,
                                     FormComponentChangeListener<FormRadioButtonGroup<E>, E> listener,
                                     List<FormTransformer<FormRadioButtonGroup<E>, E, ?>> transformers,
                                     @Nullable BooleanBinding visible) {
        super(dataKey, data, currentData -> {
            var component = new FormRadioButtonGroup<>(
                currentData.label,
                currentData.required,
                currentData.options(),
                currentData.optionLabelProvider,
                currentData.translateOptions,
                currentData.spacing
            );

            E defaultSelection = currentData.selectedSupplier.get();
            if (defaultSelection != null) {
                component.setValue(defaultSelection);
            }

            return component;
        }, validator, listener, transformers, visible);
    }

    @Override
    public ObservableValue<FormRadioButtonGroup<E>> getValidationNode() {
        return componentProperty();
    }

    @Override
    protected void applyListener(FormComponentChangeListener<FormRadioButtonGroup<E>, E> listener) {
        AtomicReference<ChangeListener<E>> listenerRef = new AtomicReference<>();
        componentProperty().addListener((observable, oldComponent, newComponent) -> {
            if (oldComponent != null && listenerRef.get() != null) {
                oldComponent.valueProperty().removeListener(listenerRef.get());
            }

            if (newComponent != null) {
                ChangeListener<E> changeListener = (obs, oldValue, newValue) ->
                    listener.changed(newComponent, obs, oldValue, newValue);
                listenerRef.set(changeListener);
                newComponent.valueProperty().addListener(changeListener);
            }
        });
    }

    @Override
    protected void bindToFormData(FormData formData) {
        AtomicReference<ChangeListener<E>> valueListener = new AtomicReference<>();

        Runnable attachListener = () -> {
            FormRadioButtonGroup<E> component = componentProperty().get();
            if (component == null) {
                return;
            }

            ChangeListener<E> changeListener = (observable, oldValue, newValue) ->
                formData.add(dataKey, newValue);
            valueListener.set(changeListener);
            component.valueProperty().addListener(changeListener);
            formData.add(dataKey, component.getValue());
        };

        componentProperty().addListener((observable, oldComponent, newComponent) -> {
            if (oldComponent != null && valueListener.get() != null) {
                oldComponent.valueProperty().removeListener(valueListener.get());
            }

            attachListener.run();
        });

        attachListener.run();
    }

    @Override
    public void reset() {
        E selection = getData().selectedSupplier.get();
        getComponent().setValue(selection);
    }

    /**
     * Builder for {@link RadioButtonGroupComponent}.
     */
    public static class Builder<E extends Enum<E>> implements FormComponentBuilder<RadioButtonGroupComponent<E>, FormRadioButtonGroup<E>, E, Builder<E>> {
        private final String dataKey;
        private final Data<E> data;
        private final List<FormTransformer<FormRadioButtonGroup<E>, E, ?>> transformers = new ArrayList<>();
        private FormComponentValidator<FormRadioButtonGroup<E>> validator;
        private FormComponentChangeListener<FormRadioButtonGroup<E>, E> listener;
        private BooleanBinding visible;

        public Builder(@NotNull String dataKey, @NotNull String label, @NotNull Class<E> enumClass) {
            this.dataKey = dataKey;
            this.data = new Data<>(label, enumClass);
            deriveLocalizationDefaults(enumClass);
        }

        private void deriveLocalizationDefaults(Class<E> enumClass) {
            try {
                Method method = enumClass.getMethod("getLocalizationKey");
                if (method.getReturnType() == String.class) {
                    data.optionLabelProvider(enumValue -> {
                        try {
                            return (String) method.invoke(enumValue);
                        } catch (IllegalAccessException | InvocationTargetException exception) {
                            ServiceLocator.getService(Logger.class).warn("Failed to invoke getLocalizationKey for {}", enumValue, exception);
                            return enumValue.name();
                        }
                    });
                    data.translateOptions(true);
                    return;
                }
            } catch (NoSuchMethodException ignored) {
                // Default fallback handled below.
            }

            data.optionLabelProvider(Enum::name);
        }

        @Override
        public String dataKey() {
            return dataKey;
        }

        public Builder<E> options(List<E> options) {
            data.options(options);
            return this;
        }

        public Builder<E> options(E[] options) {
            return options(Arrays.asList(options));
        }

        public Builder<E> required(boolean required) {
            data.required(required);
            return this;
        }

        public Builder<E> required() {
            return required(true);
        }

        public Builder<E> selected(E selected) {
            return selected(() -> selected);
        }

        public Builder<E> selected(Supplier<E> supplier) {
            data.selectedSupplier(Objects.requireNonNull(supplier));
            return this;
        }

        public Builder<E> spacing(double spacing) {
            data.spacing(spacing);
            return this;
        }

        public Builder<E> optionLabelProvider(Function<E, String> provider) {
            data.optionLabelProvider(provider);
            return this;
        }

        public Builder<E> translateOptions(boolean translate) {
            data.translateOptions(translate);
            return this;
        }

        @Override
        public Builder<E> validator(FormComponentValidator<FormRadioButtonGroup<E>> validator) {
            this.validator = validator;
            return this;
        }

        @Override
        public Builder<E> listener(FormComponentChangeListener<FormRadioButtonGroup<E>, E> listener) {
            this.listener = listener;
            return this;
        }

        @Override
        public <X> Builder<E> addTransformer(ObservableValue<FormRadioButtonGroup<E>> fromComponent, Consumer<X> toComponentFunction, Function<E, X> valueMapper) {
            transformers.add(new FormTransformer<>(fromComponent, FormRadioButtonGroup::getValue, toComponentFunction, valueMapper));
            return this;
        }

        @Override
        public <U extends Node, X> Builder<E> addTransformer(ObservableValue<FormRadioButtonGroup<E>> fromComponent, ObservableValue<U> toComponent, Function<E, X> valueMapper) {
            transformers.add(new FormTransformer<>(fromComponent, FormRadioButtonGroup::getValue, value -> {
                Node target = toComponent.getValue();
                if (target instanceof HasSetValue hasSetValue) {
                    hasSetValue.setValue(value);
                }
            }, valueMapper));
            return this;
        }

        @Override
        public Builder<E> visible(BooleanBinding visible) {
            this.visible = visible;
            return this;
        }

        @Override
        public RadioButtonGroupComponent<E> build() {
            return new RadioButtonGroupComponent<>(dataKey, data, validator, listener, transformers, visible);
        }
    }

    /**
     * Mutable data backing the component factory.
     */
    public static class Data<E extends Enum<E>> {
        private final String label;
        private final Class<E> enumClass;
        private boolean required;
        private boolean translateOptions;
        private double spacing = 12;
        private Supplier<E> selectedSupplier = () -> null;
        private Function<E, String> optionLabelProvider = Enum::name;
        private List<E> options;

        public Data(String label, Class<E> enumClass) {
            this.label = label;
            this.enumClass = enumClass;
            this.options = enumClass.isEnum() ? List.of(enumClass.getEnumConstants()) : List.of();
        }

        public void required(boolean required) {
            this.required = required;
        }

        public void translateOptions(boolean translateOptions) {
            this.translateOptions = translateOptions;
        }

        public void spacing(double spacing) {
            this.spacing = spacing;
        }

        public void selectedSupplier(Supplier<E> selectedSupplier) {
            this.selectedSupplier = Objects.requireNonNull(selectedSupplier);
        }

        public void optionLabelProvider(Function<E, String> optionLabelProvider) {
            this.optionLabelProvider = Objects.requireNonNull(optionLabelProvider);
        }

        public void options(List<E> options) {
            this.options = List.copyOf(Objects.requireNonNull(options));
        }

        public List<E> options() {
            if (options != null)
                return options;

            return List.of(enumClass.getEnumConstants());
        }
    }
}
