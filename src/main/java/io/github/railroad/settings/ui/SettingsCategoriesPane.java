package io.github.railroad.settings.ui;

import io.github.railroad.settings.SettingsCategory;
import javafx.scene.Parent;
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
        this.backButton = new Button("Back");
        this.backButton.setGraphic(new FontIcon(FontAwesomeSolid.BACKSPACE));
        this.backButton.prefWidthProperty().bind(widthProperty());

        this.listView.getItems().addAll(SettingsCategory.values());
        this.listView.prefHeightProperty().bind(heightProperty());
        this.listView.getSelectionModel().selectFirst();
        this.listView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
                parent.settingsCategoryProperty().set(newValue));
        this.listView.setCellFactory(param -> new SettingsCategoryCell());

        getChildren().addAll(backButton, new Separator(), listView);
    }

    public Button getBackButton() {
        return backButton;
    }

    public ListView<SettingsCategory> getListView() {
        return listView;
    }

    public static class SettingsCategoryCell extends ListCell<SettingsCategory> {
        private final FontIcon icon = new FontIcon();

        public SettingsCategoryCell() {
            icon.setIconSize(24);
        }

        @Override
        protected void updateItem(SettingsCategory item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                icon.setIconCode(item.getIcon());
                icon.setIconColor(item.getColor());
                setGraphic(icon);

                setText(item.getName());
            }
        }
    }
}
