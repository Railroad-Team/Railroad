package io.github.railroad.ui.form.impl;

import io.github.railroad.ui.BrowseButton;
import io.github.railroad.ui.form.FormComponent;
import io.github.railroad.ui.form.FormComponentChangeListener;
import io.github.railroad.ui.form.FormComponentValidator;
import io.github.railroad.ui.form.ui.FormDirectoryChooser;
import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

public class DirectoryChooserComponent extends FormComponent<FormDirectoryChooser, DirectoryChooserComponent.Data, TextField, String> {
    public DirectoryChooserComponent(Data data, FormComponentValidator<TextField> validator, FormComponentChangeListener<TextField, String> listener, Property<TextField> bindTextFieldTo, Property<BrowseButton> bindBrowseButtonTo) {
        super(data, dataCurrent -> new FormDirectoryChooser(dataCurrent.label, dataCurrent.required, dataCurrent.defaultPath, dataCurrent.includeButton), validator, listener);

        if(bindTextFieldTo != null) {
            bindTextFieldTo.bind(componentProperty().map(FormDirectoryChooser::getPrimaryComponent));
        }

        if(bindBrowseButtonTo != null) {
            bindBrowseButtonTo.bind(componentProperty().map(FormDirectoryChooser::getBrowseButton));
        }
    }

    @Override
    public ObservableValue<TextField> getValidationNode() {
        return componentProperty().map(FormDirectoryChooser::getPrimaryComponent);
    }

    @Override
    protected void applyListener(FormComponentChangeListener<TextField, String> listener) {
        AtomicReference<ChangeListener<String>> listenerRef = new AtomicReference<>();
        componentProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.getPrimaryComponent().textProperty().removeListener(listenerRef.get());
            }

            if (newValue != null) {
                listenerRef.set((observable1, oldValue1, newValue1) ->
                        listener.changed(newValue.getPrimaryComponent(), observable1, oldValue1, newValue1));

                newValue.getPrimaryComponent().textProperty().addListener(listenerRef.get());
            }
        });
    }

    public static class Builder {
        private final Data data;
        private FormComponentValidator<TextField> validator = null;
        private FormComponentChangeListener<TextField, String> listener = null;
        private Property<TextField> bindTextFieldTo;
        private Property<BrowseButton> bindBrowseButtonTo;

        public Builder(@NotNull String label) {
            this.data = new Data(label);
        }

        public Builder defaultPath(@Nullable String defaultPath) {
            this.data.defaultPath(defaultPath);
            return this;
        }

        public Builder required(boolean required) {
            this.data.required(required);
            return this;
        }

        public Builder includeButton(boolean includeButton) {
            this.data.includeButton(includeButton);
            return this;
        }

        public Builder validator(@NotNull FormComponentValidator<TextField> validator) {
            this.validator = validator;
            return this;
        }

        public Builder listener(@NotNull FormComponentChangeListener<TextField, String> listener) {
            this.listener = listener;
            return this;
        }

        public Builder bindTextFieldTo(@NotNull Property<TextField> bindTextFieldTo) {
            this.bindTextFieldTo = bindTextFieldTo;
            return this;
        }

        public Builder bindBrowseButtonTo(@NotNull Property<BrowseButton> bindBrowseButtonTo) {
            this.bindBrowseButtonTo = bindBrowseButtonTo;
            return this;
        }

        public DirectoryChooserComponent build() {
            return new DirectoryChooserComponent(data, validator, listener, bindTextFieldTo, bindBrowseButtonTo);
        }

        public Builder required() {
            return required(true);
        }
    }

    public static class Data {
        private final String label;
        private String defaultPath = null;
        private boolean required = false;
        private boolean includeButton = true;

        public Data(@NotNull String label) {
            this.label = label;
        }

        public Data defaultPath(@Nullable String defaultPath) {
            this.defaultPath = defaultPath;
            return this;
        }

        public Data required(boolean required) {
            this.required = required;
            return this;
        }

        public Data includeButton(boolean includeButton) {
            this.includeButton = includeButton;
            return this;
        }
    }
}