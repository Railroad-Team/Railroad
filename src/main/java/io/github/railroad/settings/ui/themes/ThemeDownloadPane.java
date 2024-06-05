package io.github.railroad.settings.ui.themes;

import com.google.gson.JsonObject;
import io.github.railroad.ui.defaults.RRListView;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.ui.localized.LocalizedLabel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;

public class ThemeDownloadPane {
    public ThemeDownloadPane() {
        var stage = new Stage();
        var pane = new RRVBox();
        var listView = new RRListView<JsonObject>();

        List<JsonObject> themeList = ThemeDownloadManager.fetchThemes("https://api.github.com/repos/Railroad-Team/Themes/contents");

        if(themeList.isEmpty()) {
            listView.setVisible(false);
            pane.getChildren().add(new LocalizedLabel("railroad.home.themebox.notfound"));
        }
        ObservableList<JsonObject> observableThemes = FXCollections.observableList(themeList);

        stage.setTitle("Download themes");
        listView.setCellFactory(t -> new ThemeDownloadCell());

        var title = new LocalizedLabel("railroad.home.settings.appearance.downloadtheme");
        title.setAlignment(Pos.TOP_CENTER);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 20; -fx-alignment: center");

        listView.getItems().addAll(observableThemes);

        pane.getChildren().addAll(title, listView);
        pane.setAlignment(Pos.TOP_CENTER);

        var scene = new Scene(pane, 450, 450);

        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();
    }
}