package io.github.railroad.project.ui.welcome;

import io.github.railroad.Railroad;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRListView;
import io.github.railroad.ui.defaults.RRSeparator;
import io.github.railroad.ui.defaults.RRVBox;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class WelcomeLeftPane extends RRVBox {
    private final RRListView<MenuType> listView = new RRListView<>();

    public WelcomeLeftPane() {
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

        listView.getItems().addAll(MenuType.values());
        listView.setCellFactory(param -> new MenuTypeCell());

        setPrefWidth(200);
        setAlignment(Pos.TOP_CENTER);
        RRVBox.setVgrow(listView, Priority.ALWAYS);

        getChildren().addAll(
                topBox,
                new RRSeparator(),
                listView
        );
    }

    public RRListView<MenuType> getListView() {
        return listView;
    }

    public enum MenuType {
        NEW_PROJECT("New Project", FontAwesomeSolid.PLUS, Color.BLACK),
        OPEN_PROJECT("Open Project", FontAwesomeSolid.FOLDER_OPEN, Color.BLACK),
        IMPORT_PROJECT("Import Project", FontAwesomeSolid.FILE_IMPORT, Color.BLACK),
        SETTINGS("Settings", FontAwesomeSolid.COG, Color.BLACK);

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