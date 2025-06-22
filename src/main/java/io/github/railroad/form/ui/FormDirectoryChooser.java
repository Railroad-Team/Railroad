package io.github.railroad.form.ui;

import io.github.railroad.localization.ui.LocalizedTextField;
import io.github.railroad.ui.BrowseButton;
import io.github.railroad.ui.defaults.RRHBox;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.HashMap;
import java.util.Map;

@Getter
public class FormDirectoryChooser extends InformativeLabeledHBox<FormDirectoryChooser.TextFieldWithButton> {
    public FormDirectoryChooser(String labelKey, boolean required, @Nullable String defaultPath, boolean includeButton) {
        super(labelKey, required, createParams(defaultPath, includeButton));
    }

    private static Map<String, Object> createParams(@Nullable String defaultPath, boolean includeButton) {
        Map<String, Object> params = new HashMap<>();
        if (defaultPath != null) {
            params.put("defaultPath", defaultPath);
        }

        params.put("includeButton", includeButton);

        return params;
    }

    @Override
    public TextFieldWithButton createPrimaryComponent(Map<String, Object> params) {
        var defaultPath = (String) params.get("defaultPath");
        var includeButton = (boolean) params.get("includeButton");

        var textField = new LocalizedTextField(null);
        if (defaultPath != null) {
            textField.setText(defaultPath);
        }

        BrowseButton browseButton = null;
        if (includeButton) {
            var browseButtonIcon = new FontIcon(FontAwesomeSolid.FOLDER_OPEN);
            browseButtonIcon.setIconSize(16);
            browseButtonIcon.setIconColor(Color.CADETBLUE);

            browseButton = new BrowseButton();
            browseButton.parentWindowProperty().bind(sceneProperty().map(Scene::getWindow));
            browseButton.textFieldProperty().set(textField);
            browseButton.browseTypeProperty().set(BrowseButton.BrowseType.DIRECTORY);
            browseButton.setGraphic(browseButtonIcon);
            browseButton.setTooltip(new Tooltip("Browse"));
        }

        var hbox = new TextFieldWithButton(textField, browseButton);
        getChildren().add(hbox);

        return hbox;
    }

    @Getter
    public static class TextFieldWithButton extends RRHBox {
        private final TextField textField;
        private final @Nullable BrowseButton browseButton;

        public TextFieldWithButton(TextField textField, @Nullable BrowseButton browseButton) {
            super(5);

            this.textField = textField;
            this.browseButton = browseButton;

            getChildren().add(textField);
            if (browseButton != null) {
                getChildren().add(browseButton);
            }
        }
    }
}
