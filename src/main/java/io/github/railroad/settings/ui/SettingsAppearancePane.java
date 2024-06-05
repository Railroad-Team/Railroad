package io.github.railroad.settings.ui;

import io.github.railroad.Railroad;
import io.github.railroad.settings.ui.themes.ThemeDownloadManager;
import io.github.railroad.settings.ui.themes.ThemeDownloadPane;
import io.github.railroad.config.ConfigHandler;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.ui.localized.LocalizedButton;
import io.github.railroad.ui.localized.LocalizedLabel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;

import java.nio.file.Path;
import java.util.List;

public class SettingsAppearancePane extends RRVBox {
    private final ComboBox<String> themeSelector = new ComboBox<>();

    public SettingsAppearancePane() {
        setSpacing(10);
        setPadding(new Insets(10));

        var themeBox = new RRVBox(10);

        var title = new LocalizedLabel("railroad.home.settings.appearance");
        title.setStyle("-fx-font-size: 20pt; -fx-font-weight: bold;");
        title.prefWidthProperty().bind(widthProperty());
        title.setAlignment(Pos.CENTER);

        var themeOption = new LocalizedLabel("railroad.home.settings.appearance.theme");
        themeOption.setStyle("-fx-font-weight: bold;");
        themeSelector.getStyleClass().add("theme-selector");
        themeSelector.setPrefWidth(180);

        List<Path> themes = ThemeDownloadManager.getDownloaded();
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
            Railroad.updateTheme(theme);
        });

        // Download theme button
        var downloadThemes = new LocalizedButton("railroad.home.settings.appearance.downloadtheme");
        downloadThemes.setOnAction(event -> new ThemeDownloadPane());

        themeBox.getChildren().addAll(themeOption, themeSelector, downloadThemes);
        getChildren().addAll(title, themeBox);
    }
}
