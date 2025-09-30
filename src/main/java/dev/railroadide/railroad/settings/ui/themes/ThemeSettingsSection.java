package dev.railroadide.railroad.settings.ui.themes;

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

    public ThemeSettingsSection(String initialTheme) {
        setSpacing(24);
        setAlignment(Pos.TOP_LEFT);
        setPadding(new Insets(0));

        themeSelector = new ThemeSelector(initialTheme);
        VBox.setVgrow(themeSelector, Priority.ALWAYS);
        getChildren().add(themeSelector);
    }

    public String getSelectedTheme() {
        return themeSelector.getSelectedTheme();
    }

    public void setSelectedTheme(String theme) {
        themeSelector.setSelectedTheme(theme);
    }
}
