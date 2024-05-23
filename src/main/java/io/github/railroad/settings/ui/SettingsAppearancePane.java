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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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

        List<Path> themes = new ArrayList<>();
        try (Stream<Path> files = Files.list(Path.of("themes"))) {
             files.filter(file -> file.toString().endsWith(".css")).forEach(themes::add);
        } catch (IOException e) {
            e.printStackTrace();
        }
        themeSelector.setPrefWidth(180);

        if(themeSelector.getItems().size() < themes.size() + 2) {
            themeSelector.getItems().clear();

            themeSelector.getItems().addAll("default-dark", "default-light");

            for (Path theme : themes) {
                String name = theme.getFileName().toString().replace(".css", "");;
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

            ConfigHandler.updateConfig();

            themeSelector.getItems().remove(theme);
            themeSelector.getItems().addFirst(theme);


            Railroad.setStyle(theme);
        });

        themeBox.getChildren().addAll(themeOption, themeSelector);

        getChildren().addAll(title, themeBox);
    }
}
