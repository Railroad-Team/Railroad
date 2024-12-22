package io.github.railroad.settings.ui.themes;

import io.github.railroad.ui.defaults.RRVBox;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import static io.github.railroad.Railroad.LOGGER;

public class ThemeExamplePane {
    public ThemeExamplePane(final String theme) {
        var stage = new Stage();
        var pane = new RRVBox();

        //TODO add a way to fetch themes from a different source
        var image = new Image("https://github.com/Railroad-Team/Themes/blob/main/" + theme.replace(".css", "") + ".png?raw=true");
        LOGGER.debug("Getting image {}", image.getUrl());
        var view = new ImageView(image);

        view.fitHeightProperty().bind(stage.heightProperty());
        view.fitWidthProperty().bind(stage.widthProperty());
        view.setPreserveRatio(true);

        pane.getChildren().add(view);

        var scene = new Scene(pane, 500, 350);
        stage.setTitle("Theme Example");
        stage.setScene(scene);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.show();
    }
}
