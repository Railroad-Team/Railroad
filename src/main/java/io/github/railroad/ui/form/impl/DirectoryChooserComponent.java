package io.github.railroad.ui.form.impl;

import io.github.railroad.ui.BrowseButton;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.form.FormComponent;
import io.github.railroad.ui.form.FormComponentChangeListener;
import io.github.railroad.ui.form.FormComponentValidator;
import io.github.railroad.ui.localized.LocalizedTextField;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.concurrent.atomic.AtomicReference;

public class DirectoryChooserComponent extends FormComponent<DirectoryChooserComponent.FormDirectoryChooser, DirectoryChooserComponent.Data, TextField, String> {
    public DirectoryChooserComponent(Data data, FormComponentValidator<TextField> validator, FormComponentChangeListener<TextField, String> listener) {
        super(data, dataCurrent -> new FormDirectoryChooser(dataCurrent.label, dataCurrent.defaultPath, dataCurrent.required, dataCurrent.includeButton), validator, listener);
    }

    @Override
    public ObservableValue<TextField> getValidationNode() {
        return componentProperty().map(FormDirectoryChooser::getTextField);
    }

    @Override
    protected void applyListener(FormComponentChangeListener<TextField, String> listener) {
        AtomicReference<ChangeListener<String>> listenerRef = new AtomicReference<>();
        componentProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.textField.textProperty().removeListener(listenerRef.get());
            }

            if (newValue != null) {
                listenerRef.set((observable1, oldValue1, newValue1) ->
                        listener.changed(newValue.textField, observable1, oldValue1, newValue1));

                newValue.textField.textProperty().addListener(listenerRef.get());
            }
        });
    }

    @Getter
    public static class FormDirectoryChooser extends RRHBox {
        private final TextField textField;
        private final BrowseButton browseButton;
        private final Label label;

        public FormDirectoryChooser(@NotNull String label, @Nullable String defaultPath, boolean required, boolean includeButton) {
            setSpacing(10);

            this.label = createLabel(this, label, required);

            this.textField = new LocalizedTextField(null);
            if (defaultPath != null) {
                this.textField.setText(defaultPath);
            }

            getChildren().add(textField);

            if(includeButton) {
                var browseButtonIcon = new FontIcon(FontAwesomeSolid.FOLDER_OPEN);
                browseButtonIcon.setIconSize(16);
                browseButtonIcon.setIconColor(Color.CADETBLUE);

                this.browseButton = new BrowseButton();
                browseButton.parentWindowProperty().bind(sceneProperty().map(Scene::getWindow));
                browseButton.textFieldProperty().set(textField);
                browseButton.browseTypeProperty().set(BrowseButton.BrowseType.DIRECTORY);
                browseButton.setGraphic(browseButtonIcon);
                browseButton.setTooltip(new Tooltip("Browse"));

                getChildren().add(browseButton);
            } else {
                this.browseButton = null;
            }
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