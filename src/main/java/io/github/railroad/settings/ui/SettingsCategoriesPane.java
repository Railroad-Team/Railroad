package io.github.railroad.settings.ui;

import io.github.railroad.settings.SettingsCategory;
import io.github.railroad.ui.localized.LocalizedButton;
import io.github.railroad.ui.localized.LocalizedListCell;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class SettingsCategoriesPane extends VBox {
    private final Button backButton;
    private final ListView<SettingsCategory> listView = new ListView<>();

    public SettingsCategoriesPane(SettingsPane parent) {
        setPadding(new Insets(10));
        getStyleClass().add("settings-categories-pane");

        this.backButton = new LocalizedButton("railroad.home.settings.back");
        this.backButton.setGraphic(new FontIcon(FontAwesomeSolid.BACKSPACE));
        this.backButton.prefWidthProperty().bind(widthProperty());

        this.listView.getStyleClass().add("settings-categories-list");
        this.listView.getItems().addAll(SettingsCategory.values());
        this.listView.prefHeightProperty().bind(heightProperty());
        this.listView.getSelectionModel().selectFirst();
        this.listView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
                parent.settingsCategoryProperty().set(newValue));
        this.listView.setCellFactory(param -> new SettingsCategoryCell());

        var separator = new Separator();
        separator.setPadding(new Insets(10, -10, 10, -10));
        getChildren().addAll(backButton, separator, listView);
    }

    public Button getBackButton() {
        return backButton;
    }

    public ListView<SettingsCategory> getListView() {
        return listView;
    }

    public static class SettingsCategoryCell extends LocalizedListCell<SettingsCategory> {
        private final FontIcon icon = new FontIcon();

        public SettingsCategoryCell() {
            super(SettingsCategory::getName);
            icon.setIconSize(24);
        }

        @Override
        protected void updateItem(SettingsCategory item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                icon.setIconCode(item.getIcon());
                icon.setIconColor(item.getColor());
                setGraphic(icon);
            }
        }
    }
}
