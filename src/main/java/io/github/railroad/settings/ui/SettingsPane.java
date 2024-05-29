package io.github.railroad.settings.ui;

import io.github.railroad.settings.SettingsCategory;
import io.github.railroad.settings.ui.general.SettingsGeneralPane;
import io.github.railroad.ui.defaults.RRBorderPane;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.localized.LocalizedTextField;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;

public class SettingsPane extends RRBorderPane {
    private final ObjectProperty<SettingsCategory> settingsCategory = new SimpleObjectProperty<>(SettingsCategory.GENERAL);
    private final SettingsCategoriesPane leftPane;
    private final ScrollPane rightPane;
    private final TextField searchBox;

    public SettingsPane() {
        this.searchBox = new LocalizedTextField("railroad.home.settings.search");
        searchBox.prefWidthProperty().bind(widthProperty());
        setTop(searchBox);
        RRBorderPane.setAlignment(searchBox, Pos.CENTER);

        this.leftPane = new SettingsCategoriesPane(this);
        this.rightPane = new ScrollPane(new SettingsGeneralPane());

        leftPane.setMinWidth(200);
        leftPane.setMaxWidth(200);

        rightPane.setMinWidth(400);

        rightPane.setFitToWidth(true);
        rightPane.setFitToHeight(true);

        rightPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rightPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        var contentPane = new RRHBox();
        var verticalSeparator = new Separator(Orientation.VERTICAL);
        verticalSeparator.setPadding(new Insets(0));
        contentPane.getChildren().addAll(leftPane, verticalSeparator, rightPane);
        RRHBox.setHgrow(rightPane, Priority.ALWAYS);

        setCenter(contentPane);
        RRBorderPane.setAlignment(contentPane, Pos.CENTER);

        this.settingsCategory.addListener((observable, oldValue, newValue) -> {
            Node newRightPane = switch (newValue) {
                case GENERAL -> new SettingsGeneralPane();
                case APPEARANCE -> new SettingsAppearancePane();
                case BEHAVIOR -> new SettingsBehaviorPane();
                case KEYMAPS -> new SettingsKeymapsPane();
                case PLUGINS -> new SettingsPluginsPane();
                case PROJECTS -> new SettingsProjectsPane();
                case TOOLS -> new SettingsToolsPane();
                default -> throw new IllegalStateException("Unexpected value: " + newValue);
            };

            rightPane.setContent(newRightPane);
        });
    }

    public ObjectProperty<SettingsCategory> settingsCategoryProperty() {
        return this.settingsCategory;
    }

    public Button getBackButton() {
        return leftPane.getBackButton();
    }
}
