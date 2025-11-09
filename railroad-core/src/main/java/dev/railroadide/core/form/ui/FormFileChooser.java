package dev.railroadide.core.form.ui;

import dev.railroadide.core.form.HasSetValue;
import dev.railroadide.core.ui.BrowseButton;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRTextField;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A labeled text field with an optional browse button for selecting files.
 */
@Getter
public class FormFileChooser extends InformativeLabeledHBox<FormFileChooser.TextFieldWithButton> implements HasSetValue {
    public FormFileChooser(String labelKey, boolean required, @Nullable String defaultPath, boolean includeButton) {
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

        RRTextField textField = new RRTextField();
        textField.getStyleClass().add("rr-text-field");
        if (defaultPath != null) {
            textField.setText(defaultPath);
        }

        BrowseButton browseButton = null;
        if (includeButton) {
            var browseButtonIcon = new FontIcon(FontAwesomeSolid.FILE);
            browseButtonIcon.setIconSize(16);
            browseButtonIcon.setIconColor(Color.CADETBLUE);

            browseButton = new BrowseButton();
            browseButton.parentWindowProperty().bind(sceneProperty().map(Scene::getWindow));
            browseButton.textFieldProperty().set(textField);
            browseButton.browseTypeProperty().set(BrowseButton.BrowseType.FILE);
            browseButton.getStyleClass().add("rr-button");
            browseButton.setGraphic(browseButtonIcon);
        }

        var hbox = new TextFieldWithButton(textField, browseButton);
        getChildren().add(hbox);

        return hbox;
    }

    @Override
    public void setValue(Object value) {
        Platform.runLater(() -> getPrimaryComponent().textField.setText(Objects.toString(value, "")));
    }

    /**
     * Simple container for text field + optional browse button.
     */
    @Getter
    public static class TextFieldWithButton extends RRHBox {
        private final RRTextField textField;
        private final @Nullable BrowseButton browseButton;

        public TextFieldWithButton(RRTextField textField, @Nullable BrowseButton browseButton) {
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
