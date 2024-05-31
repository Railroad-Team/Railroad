package io.github.railroad.settings.ui;

import io.github.railroad.Railroad;
import io.github.railroad.config.ConfigHandler;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.ui.localized.LocalizedLabel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class SettingsAppearancePane extends RRVBox {
    private static final ComboBox<String> themeSelector = new ComboBox<>();

    public SettingsAppearancePane() {
        var themeBox = new RRVBox(10);

        var title = new LocalizedLabel("railroad.home.settings.appearance");
        title.setStyle("-fx-font-size: 20pt; -fx-font-weight: bold;");
        title.prefWidthProperty().bind(widthProperty());
        title.setAlignment(Pos.CENTER);

        var themeOption = new LocalizedLabel("railroad.home.settings.appearance.theme");
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

            themeSelector.setValue(ConfigHandler.getConfig().getSettings().getTheme());
        }

        themeSelector.setOnAction(event -> {
            if (themeSelector.getValue() == null)
                return;

            String theme = themeSelector.getValue();
            ConfigHandler.getConfig().getSettings().setTheme(theme);
            ConfigHandler.updateConfig();
            Railroad.updateTheme(theme);
        });

        setSpacing(10);
        setPadding(new Insets(10));

        themeBox.getChildren().addAll(themeOption, themeSelector);

        getChildren().addAll(title, themeBox);
    }
}
