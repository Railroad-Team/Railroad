package io.github.railroad.project.ui.welcome;

import io.github.railroad.Railroad;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class WelcomeLeftPane extends VBox {
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

        setPrefWidth(200);
        setAlignment(Pos.TOP_CENTER);
        getChildren().addAll(topBox, new Separator());
    }
}