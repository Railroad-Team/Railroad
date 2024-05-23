package io.github.railroad.settings.ui;

import io.github.railroad.Railroad;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.utility.ConfigHandler;
import javafx.geometry.Pos;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

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

        File[] themes = new File[0];
        try {
             themes = Files.list(new File("themes").toPath())
                    .filter(file -> file.toString().endsWith(".css"))
                    .map(file -> file.toFile()).toArray(File[]::new);
        } catch (IOException e) {
            e.printStackTrace();
        }
        themeSelector.setPrefWidth(180);

        if(themeSelector.getItems().size() < themes.length + 2){
            themeSelector.getItems().clear();

            themeSelector.getItems().addAll("default-dark", "default-light");

            for (var theme : themes) {
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
            if(themeSelector.getValue() == null) return;
            var theme = themeSelector.getValue().toString();

            var config = ConfigHandler.getConfigJson();
            config.get("settings").getAsJsonObject().addProperty("theme", theme);

            ConfigHandler.updateConfig(config);

            themeSelector.getItems().remove(theme);
            themeSelector.getItems().addFirst(theme);


            Railroad.setStyle(theme);
        });

        themeBox.getChildren().addAll(themeOption, themeSelector);

        getChildren().addAll(title, themeBox);
    }
}
