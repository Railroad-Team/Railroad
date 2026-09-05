package dev.railroadide.railroad.theme.ui;

import dev.railroadide.railroad.ui.RRVBox;
import javafx.geometry.Pos;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;

/**
 * A modern theme settings section that provides comprehensive theme management.
 * Features theme selection, preview, download, and management capabilities.
 */
public class ThemeSettingsSection extends RRVBox {
    @Getter
    private final ThemeSelector themeSelector;

    /**
     * Constructs a ThemeSettingsSection with the specified initial theme.
     *
     * @param initialTheme The initial theme to be selected in the theme selector.
     */
    public ThemeSettingsSection(String initialTheme) {
        setAlignment(Pos.TOP_LEFT);
        getStyleClass().add("theme-settings-section");

        themeSelector = new ThemeSelector(initialTheme);
        VBox.setVgrow(themeSelector, Priority.ALWAYS);
        getChildren().add(themeSelector);
    }

    /**
     * Retrieves the currently selected theme from the theme selector.
     *
     * @return The name of the selected theme.
     */
    public String getSelectedTheme() {
        return themeSelector.getSelectedTheme();
    }

    /**
     * Sets the selected theme in the theme selector.
     *
     * @param theme The name of the theme to be selected.
     */
    public void setSelectedTheme(String theme) {
        themeSelector.setSelectedTheme(theme);
    }
}
