package io.github.railroad.settings.ui;

import io.github.railroad.settings.SettingsCategory;
import io.github.railroad.ui.defaults.RRListCell;
import io.github.railroad.ui.defaults.RRListView;
import io.github.railroad.ui.defaults.RRSeparator;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class SettingsCategoriesPane extends VBox {
    private final Button backButton;
    private final RRListView<SettingsCategory> listView = new RRListView<>();

    public SettingsCategoriesPane(SettingsPane parent) {
        this.backButton = new Button("Back");
        this.backButton.setGraphic(new FontIcon(FontAwesomeSolid.ARROW_LEFT));
        HBox.setHgrow(this.backButton, Priority.ALWAYS);

        this.listView.getItems().addAll(SettingsCategory.values());
        VBox.setVgrow(this.listView, Priority.ALWAYS);
        this.listView.getSelectionModel().selectFirst();

        this.listView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
                parent.settingsCategoryProperty().set(newValue));
        this.listView.setCellFactory(param -> new SettingsCategoryCell());

        getChildren().addAll(
                backButton,
                new RRSeparator(),
                listView
        );
    }

    public Button getBackButton() {
        return backButton;
    }

    public RRListView<SettingsCategory> getListView() {
        return listView;
    }

    public static class SettingsCategoryCell extends RRListCell<SettingsCategory> {
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
                icon.getStyleClass().add("foreground-2");
                setGraphic(icon);
                setText(item.getName());
            }
        }
    }
}
