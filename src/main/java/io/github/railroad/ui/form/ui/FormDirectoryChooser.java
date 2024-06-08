package io.github.railroad.ui.form.ui;

import io.github.railroad.ui.BrowseButton;
import io.github.railroad.ui.localized.LocalizedTextField;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

@Getter
public class FormDirectoryChooser extends InformativeLabeledHBox<TextField> {
    private final String defaultPath;
    private final boolean includeButton;
    private BrowseButton browseButton;

    public FormDirectoryChooser(String labelKey, boolean required, @Nullable String defaultPath, boolean includeButton) {
        super(labelKey, required);
        this.defaultPath = defaultPath;
        this.includeButton = includeButton;
    }

    @Override
    public TextField createPrimaryComponent() {
        var textField = new LocalizedTextField(null);
        if (this.defaultPath != null) {
            textField.setText(this.defaultPath);
        }

        getChildren().add(textField);

        if(this.includeButton) {
            var browseButtonIcon = new FontIcon(FontAwesomeSolid.FOLDER_OPEN);
            browseButtonIcon.setIconSize(16);
            browseButtonIcon.setIconColor(Color.CADETBLUE);

            var browseButton = new BrowseButton();
            browseButton.parentWindowProperty().bind(sceneProperty().map(Scene::getWindow));
            browseButton.textFieldProperty().set(textField);
            browseButton.browseTypeProperty().set(BrowseButton.BrowseType.DIRECTORY);
            browseButton.setGraphic(browseButtonIcon);
            browseButton.setTooltip(new Tooltip("Browse"));

            getChildren().add(browseButton);
        }

        return textField;
    }
}
