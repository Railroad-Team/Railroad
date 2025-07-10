package io.github.railroad.settings.ui.themes;

import io.github.railroad.core.ui.localized.LocalizedLabel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;

/**
 * A modern theme settings section that provides comprehensive theme management.
 * Features theme selection, preview, download, and management capabilities.
 */
public class ThemeSettingsSection extends VBox {
    @Getter
    private final ThemeSelector themeSelector;
    private LocalizedLabel descriptionLabel;

    public ThemeSettingsSection() {
        setSpacing(24);
        setAlignment(Pos.TOP_LEFT);
        setPadding(new Insets(0));

        var header = createHeader();
        getChildren().add(header);

        themeSelector = new ThemeSelector();
        VBox.setVgrow(themeSelector, Priority.ALWAYS);
        getChildren().add(themeSelector);
    }

    private VBox createHeader() {
        var header = new VBox(8);
        header.setAlignment(Pos.TOP_LEFT);

        descriptionLabel = new LocalizedLabel("railroad.appearance.themes.description");
        descriptionLabel.getStyleClass().add("theme-description-label");

        header.getChildren().addAll(descriptionLabel);
        return header;
    }

    public String getSelectedTheme() {
        return themeSelector.getSelectedTheme();
    }

    public void setSelectedTheme(String theme) {
        themeSelector.setSelectedTheme(theme);
    }
} 