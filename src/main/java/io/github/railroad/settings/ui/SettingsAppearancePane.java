package io.github.railroad.settings.ui;

import io.github.railroad.Railroad;
import io.github.railroad.config.ConfigHandler;
import io.github.railroad.settings.ui.themes.ThemeDownloadManager;
import io.github.railroad.settings.ui.themes.ThemeDownloadPane;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.ui.localized.LocalizedButton;
import io.github.railroad.ui.localized.LocalizedLabel;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static io.github.railroad.Railroad.LOGGER;

public class SettingsAppearancePane extends RRVBox {
    private static final ComboBox<String> themeSelector = new ComboBox<>();

    public ObservableList<Node> getcChildren() {
        return super.getChildren();
    }

    public SettingsAppearancePane() {
        var themeBox = new RRVBox(10);

        var title = new LocalizedLabel("railroad.home.settings.appearance");
        title.setStyle("-fx-font-size: 20pt; -fx-font-weight: bold;");
        title.prefWidthProperty().bind(widthProperty());
        title.setAlignment(Pos.CENTER);

        var themeOption = new LocalizedLabel("railroad.home.settings.appearance.theme");
        themeOption.setStyle("-fx-font-weight: bold;");
        themeSelector.setStyle(".list-view { -fx-pref-height: 400 }");

        var downloadThemes = new LocalizedButton("railroad.home.settings.appearance.downloadtheme");

        List<Path> themes = new ArrayList<>();

        if(Files.exists(ThemeDownloadManager.getThemesDir())) {
            try (Stream<Path> files = Files.list(ThemeDownloadManager.getThemesDir())) {
                files.filter(file -> file.toString().endsWith(".css")).forEach(themes::add);
            } catch (IOException exception) {
                LOGGER.error("Failed to load themes", exception);
            }
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
            ConfigHandler.saveConfig();
            Railroad.updateTheme(theme);
        });

        //Download theme button
        downloadThemes.setOnAction(a -> new ThemeDownloadPane());

        setSpacing(10);
        setPadding(new Insets(10));

        themeBox.getChildren().addAll(themeOption, themeSelector, downloadThemes);

        getChildren().addAll(title, themeBox);
    }
}
