package io.github.railroad.settings.ui.themes;

import io.github.railroad.ui.defaults.RRListView;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.ui.localized.LocalizedLabel;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;

public class ThemeDownloadPane {
    public ThemeDownloadPane() {
        var stage = new Stage();
        var pane = new RRVBox();
        var listView = new RRListView<Theme>();

        List<Theme> themes = ThemeDownloadManager.fetchThemes("https://api.github.com/repos/Railroad-Team/Themes/contents");
        if(themes.isEmpty()) {
            listView.setVisible(false);
            pane.getChildren().add(new LocalizedLabel("railroad.home.settings.appearance.notfound"));
        }

        stage.setTitle("Download themes");
        listView.setCellFactory(theme -> new ThemeDownloadCell());

        var title = new LocalizedLabel("railroad.home.settings.appearance.downloadtheme");
        title.setAlignment(Pos.TOP_CENTER);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 20; -fx-alignment: center");

        listView.getItems().addAll(themes);

        pane.getChildren().addAll(title, listView);
        pane.setAlignment(Pos.TOP_CENTER);

        var scene = new Scene(pane, 450, 450);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();
    }
}