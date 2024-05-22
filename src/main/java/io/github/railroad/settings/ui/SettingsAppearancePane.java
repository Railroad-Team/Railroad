package io.github.railroad.settings.ui;

import io.github.railroad.Railroad;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.utility.ConfigHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;

import java.io.File;
import java.util.Arrays;

public class SettingsAppearancePane extends RRVBox {
    private final Label title = new Label("Appearance");

    private static ChoiceBox themeSelector = new ChoiceBox<String>();
    private final Label themeOption = new Label("Select a theme:");

    public SettingsAppearancePane() {
        super();

        var themeBox = new RRVBox();

        title.setStyle("-fx-font-size: 20pt; -fx-font-weight: bold;");
        title.prefWidthProperty().bind(widthProperty());
        title.setAlignment(Pos.CENTER);

        themeBox.setStyle("-fx-padding: 0 0 0 10");

        var themesDir = new File("themes").listFiles();
        themeSelector.setPrefWidth(180);

        if(themeSelector.getItems().size() < themesDir.length){
            themeSelector.getItems().clear();

            themeSelector.getItems().addAll("default-dark", "default-light");

            for (var theme : Arrays.stream(themesDir).filter(file -> file.getName().endsWith(".css")).toArray(File[]::new)) {
                String name = theme.getName().replace(".css", "");
                themeSelector.getItems().add(name);
            }

            var config = ConfigHandler.getConfigJson();
            var theme = config.get("settings").getAsJsonObject().get("theme").getAsString();

            themeSelector.getItems().remove(theme);
            themeSelector.getItems().addFirst(theme);
            themeSelector.setValue(theme);
        }

        themeSelector.setOnAction(event -> {
            var theme = themeSelector.getValue().toString();

            var config = ConfigHandler.getConfigJson();
            config.get("settings").getAsJsonObject().addProperty("theme", theme);

            ConfigHandler.updateConfig(config);

            themeSelector.getItems().remove(theme);
            themeSelector.getItems().addFirst(theme);

            String themePath;
            if(theme.startsWith("default")){
                themePath = Railroad.getResource("styles/" + theme + ".css").toExternalForm().toString();
            } else {
                themePath = new File("themes/" + theme + ".css").toURI().toString();
            }

            getScene().getStylesheets().clear();
            getScene().getStylesheets().add(themePath);
        });

        themeBox.getChildren().addAll(themeOption, themeSelector);

        getChildren().addAll(title, themeBox);
    }
}
