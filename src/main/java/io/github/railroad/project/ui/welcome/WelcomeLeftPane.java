package io.github.railroad.project.ui.welcome;

import io.github.railroad.Railroad;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

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

        setPrefWidth(200);
        setAlignment(Pos.TOP_CENTER);
        getChildren().addAll(topBox, new Separator(), listView);
        VBox.setVgrow(listView, Priority.ALWAYS);
    }

    public ListView<MenuType> getListView() {
        return listView;
    }

    public enum MenuType {
        HOME, SETTINGS;

        private final String name;

        MenuType(String name) {
            this.name = name;
        }

        MenuType() {
            this.name = name().charAt(0) + name().substring(1).toLowerCase();
        }

        public String getName() {
            return this.name;
        }
    }
}