package io.github.railroad.welcome;

import io.github.railroad.Railroad;
import io.github.railroad.localization.ui.LocalizedListCell;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRListView;
import io.github.railroad.ui.defaults.RRVBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import lombok.Getter;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class WelcomeLeftPane extends RRVBox {
    @Getter
    private final RRListView<MenuType> listView;

    public WelcomeLeftPane() {
        setPadding(new Insets(10));
        getStyleClass().add("welcome-left-pane");

        var topBox = new RRVBox();
        topBox.setPrefHeight(80);

        var hbox = new RRHBox();
        hbox.setAlignment(Pos.CENTER);
        hbox.getChildren().add(new ImageView(
                new Image(Railroad.getResourceAsStream("images/logo.png"), 100, 100, true, true)));

        var rightVbox = new RRVBox();
        rightVbox.setAlignment(Pos.CENTER);
        rightVbox.getChildren().add(new Label("Railroad IDE"));
        rightVbox.getChildren().add(new Label("1.0.0(dev)"));

        hbox.getChildren().add(rightVbox);
        topBox.getChildren().add(hbox);

        listView = new RRListView<>();
        listView.getItems().addAll(MenuType.values());
        listView.setCellFactory(param -> new MenuTypeCell());
        listView.getStyleClass().add("welcome-left-pane-list");

        setMinWidth(200);
        setMaxWidth(200);
        setAlignment(Pos.TOP_CENTER);
        RRVBox.setVgrow(listView, Priority.ALWAYS);

        var separator = new Separator();
        separator.setPadding(new Insets(10, -10, 10, -10));
        getChildren().addAll(
                topBox,
                separator,
                listView
        );
    }

    @Getter
    public enum MenuType {
        HOME("railroad.home.welcome.home", FontAwesomeSolid.HOME),
        NEW_PROJECT("railroad.home.welcome.newproject", FontAwesomeSolid.PLUS),
        OPEN_PROJECT("railroad.home.welcome.openproject", FontAwesomeSolid.FOLDER_OPEN),
        IMPORT_PROJECT("railroad.home.welcome.importproject", FontAwesomeSolid.FILE_IMPORT),
        SETTINGS("railroad.home.welcome.settings", FontAwesomeSolid.COG);

        private final String key;
        private final Ikon icon;
        private final Paint color;

        MenuType(String key, Ikon icon, Paint color) {
            this.key = key;
            this.icon = icon;
            this.color = color;
        }

        MenuType(String key, Ikon icon) {
            this(key, icon, Color.WHITE);
        }

    }

    public static class MenuTypeCell extends LocalizedListCell<MenuType> {
        private final FontIcon icon = new FontIcon();

        public MenuTypeCell() {
            super(MenuType::getKey);
            icon.setIconSize(24);
        }

        @Override
        protected void updateItem(MenuType item, boolean empty) {
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