package io.github.railroad.settings.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.railroad.project.data.Project;
import io.github.railroad.ui.defaults.RRListView;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.ui.localized.LocalizedLabel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Cell;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static io.github.railroad.Railroad.PROJECT_MANAGER;

public class ThemeDownloadPane {
    public ThemeDownloadPane() {
        var stage = new Stage();
        var pane = new RRVBox();
        var listView = new RRListView<JsonObject>();

        final String url = "https://api.github.com/repos/YodaForce157/railroadthemes/contents";

        JsonArray jsonRes = null;
        try {
            URL conUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) conUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();

            int resCode = connection.getResponseCode();

            if(resCode != 200) {
                throw new RuntimeException("THEME DOWNLOADER ERROR" + resCode);
            }

            String inline = "";
            Scanner scanner = new Scanner(conUrl.openStream());

            while (scanner.hasNext()) {
                inline += scanner.nextLine();
            }

            scanner.close();
            connection.disconnect();

            jsonRes = JsonParser.parseString(inline).getAsJsonArray();
        } catch (IOException e) {
            throw new RuntimeException("Could not list themes from github.");
        }
        List<JsonObject> themeList = new ArrayList<>();

        if(jsonRes == null) {
            listView.setVisible(false);
            pane.getChildren().add(new LocalizedLabel("railroad.home.settings.appearance.notfound"));
        } else {
            jsonRes.forEach(e -> themeList.add(e.getAsJsonObject()));
        }
        ObservableList<JsonObject> observableThemes = FXCollections.observableList(themeList);

        stage.setTitle("Download themes");
        listView.setCellFactory(t -> new ThemeDownloadCell());
        var title = new LocalizedLabel("railroad.home.settings.appearance.downloadtheme");

        listView.getItems().addAll(observableThemes);

        pane.getChildren().addAll(title, listView);

        stage.setScene(new Scene(pane, 450, 450));
        stage.show();
    }
}