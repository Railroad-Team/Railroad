package dev.railroadide.core.form.ui;

import dev.railroadide.core.ui.BrowseButton;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRTextField;
import dev.railroadide.core.ui.localized.LocalizedTooltip;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.HashMap;
import java.util.Map;

/**
 * A form directory chooser component that extends InformativeLabeledHBox to provide
 * a labeled text field with an optional browse button for directory selection.
 * Supports validation and modern styling.
 */
@Getter
public class FormDirectoryChooser extends InformativeLabeledHBox<FormDirectoryChooser.TextFieldWithButton> {
    /**
     * Constructs a new FormDirectoryChooser with the specified configuration.
     * 
     * @param labelKey the localization key for the label text
     * @param required whether the directory chooser is required
     * @param defaultPath the default path to display in the text field, or null for empty
     * @param includeButton whether to include a browse button for directory selection
     */
    public FormDirectoryChooser(String labelKey, boolean required, @Nullable String defaultPath, boolean includeButton) {
        super(labelKey, required, createParams(defaultPath, includeButton));
    }

    /**
     * Creates the parameters map for the directory chooser component.
     * 
     * @param defaultPath the default path to display
     * @param includeButton whether to include a browse button
     * @return a map containing the component parameters
     */
    private static Map<String, Object> createParams(@Nullable String defaultPath, boolean includeButton) {
        Map<String, Object> params = new HashMap<>();
        if (defaultPath != null) {
            params.put("defaultPath", defaultPath);
        }

        params.put("includeButton", includeButton);

        return params;
    }

    /**
     * Creates the primary directory chooser component with the specified parameters.
     * 
     * @param params a map containing the parameters for the directory chooser
     * @return a new TextFieldWithButton instance with the specified configuration
     */
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
            var browseButtonIcon = new FontIcon(FontAwesomeSolid.FOLDER_OPEN);
            browseButtonIcon.setIconSize(16);
            browseButtonIcon.setIconColor(Color.CADETBLUE);

            browseButton = new BrowseButton();
            browseButton.parentWindowProperty().bind(sceneProperty().map(Scene::getWindow));
            browseButton.textFieldProperty().set(textField);
            browseButton.browseTypeProperty().set(BrowseButton.BrowseType.DIRECTORY);
            browseButton.setGraphic(browseButtonIcon);
            browseButton.getStyleClass().add("rr-button");
        }

        var hbox = new TextFieldWithButton(textField, browseButton);
        getChildren().add(hbox);

        return hbox;
    }

    /**
     * A container component that combines a text field with an optional browse button.
     * Used to display the directory chooser interface.
     */
    @Getter
    public static class TextFieldWithButton extends RRHBox {
        private final RRTextField textField;
        private final @Nullable BrowseButton browseButton;

        /**
         * Constructs a new TextFieldWithButton with the specified components.
         * 
         * @param textField the text field for displaying the selected directory path
         * @param browseButton the browse button for opening the directory chooser, or null if not needed
         */
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
