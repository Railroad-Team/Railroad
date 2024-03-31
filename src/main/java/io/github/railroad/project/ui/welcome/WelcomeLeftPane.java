package io.github.railroad.project.ui.welcome;

import io.github.railroad.Railroad;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Objects;

public class WelcomeLeftPane extends VBox {
    private final ListView<MenuType> listView = new ListView<>();

    public WelcomeLeftPane() {
        var topBox = new VBox();
        topBox.setPrefHeight(80);

        var hbox = new HBox();
        hbox.setAlignment(Pos.CENTER);
        hbox.getChildren().add(new ImageView(new Image(Railroad.getResourceAsStream("images/logo.png"), 100, 100, true, true)));

        var rightVbox = new VBox();
        rightVbox.setAlignment(Pos.CENTER);
        rightVbox.getChildren().add(new Label("Railroad IDE"));
        rightVbox.getChildren().add(new Label("1.0.0"));

        hbox.getChildren().add(rightVbox);
        topBox.getChildren().add(hbox);

        listView.getItems().addAll(MenuType.values());
        listView.getSelectionModel().select(MenuType.HOME);
        listView.setCellFactory(param -> new MenuTypeCell());

        setPrefWidth(200);
        setAlignment(Pos.TOP_CENTER);
        getChildren().addAll(topBox, new Separator(), listView);
        VBox.setVgrow(listView, Priority.ALWAYS);
    }

    public ListView<MenuType> getListView() {
        return listView;
    }

    public enum MenuType {
        HOME("Home", FontAwesomeSolid.HOME, Color.RED),
        SETTINGS("Settings", FontAwesomeSolid.COG, Color.BLUE);

        private final String name;
        private final Ikon icon;
        private final Paint color;

        MenuType(String name, Ikon icon, Paint color) {
            this.name = name;
            this.icon = icon;
            this.color = color;
        }

        MenuType(String name, Ikon icon) {
            this(name, icon, Color.BLACK);
        }

        public String getName() {
            return this.name;
        }

        public Ikon getIcon() {
            return this.icon;
        }

        public Paint getColor() {
            return this.color;
        }
    }

    public static class MenuTypeCell extends ListCell<MenuType> {
        private final FontIcon icon = new FontIcon();

        public MenuTypeCell() {
            icon.setIconSize(24);
        }

        @Override
        protected void updateItem(MenuType item, boolean empty) {
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