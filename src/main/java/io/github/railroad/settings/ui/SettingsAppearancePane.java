package io.github.railroad.settings.ui;

import com.google.gson.JsonObject;
import io.github.railroad.Railroad;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.ui.localized.LocalizedButton;
import io.github.railroad.ui.localized.LocalizedLabel;
import io.github.railroad.utility.ConfigHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class SettingsAppearancePane extends RRVBox {
    private static final ComboBox<String> themeSelector = new ComboBox<>();
    private final Label title = new LocalizedLabel("railroad.home.settings.appearance");
    private final Label themeOption = new LocalizedLabel("railroad.home.settings.appearance.theme");
    private final Button downloadThemes = new LocalizedButton("railroad.home.settings.appearance.downloadtheme");

    public SettingsAppearancePane() {
        var themeBox = new RRVBox(10);

        title.setStyle("-fx-font-size: 20pt; -fx-font-weight: bold;");
        title.prefWidthProperty().bind(widthProperty());
        title.setAlignment(Pos.CENTER);

        themeOption.setStyle("-fx-font-weight: bold;");

        List<Path> themes = new ArrayList<>();
        try (Stream<Path> files = Files.list(Path.of("themes"))) {
            files.filter(file -> file.toString().endsWith(".css")).forEach(themes::add);
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to load themes", exception);
        }

        themeSelector.setPrefWidth(180);

        if (themeSelector.getItems().size() < themes.size() + 2) {
            themeSelector.getItems().clear();

            themeSelector.getItems().addAll("default-dark", "default-light");

            for (Path theme : themes) {
                String name = theme.getFileName().toString().replace(".css", "");
                themeSelector.getItems().add(name);
            }

            JsonObject config = ConfigHandler.getConfigJson();
            String theme = config.get("settings").getAsJsonObject().get("theme").getAsString();

            themeSelector.setValue(theme);
        }

        themeSelector.setOnAction(event -> {
            if (themeSelector.getValue() == null)
                return;

            String theme = themeSelector.getValue();

            JsonObject config = ConfigHandler.getConfigJson();
            config.get("settings").getAsJsonObject().addProperty("theme", theme);

            ConfigHandler.updateConfig();

            Railroad.setStyle(theme);
        });

        //Download theme button
        downloadThemes.setOnAction(a -> new ThemeDownloadPane());

        setSpacing(10);
        setPadding(new Insets(10));

        themeBox.getChildren().addAll(themeOption, themeSelector, downloadThemes);

        getChildren().addAll(title, themeBox);
    }
}
