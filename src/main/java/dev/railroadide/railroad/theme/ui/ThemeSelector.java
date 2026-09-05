package dev.railroadide.railroad.theme.ui;

import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.settings.handler.SettingsHandler;
import dev.railroadide.railroad.theme.ThemeDownloadManager;
import dev.railroadide.railroad.theme.ThemeManager;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.RRFormSection;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.styling.ButtonSize;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.nio.file.Path;
import java.util.List;

/**
 * A modern theme selector component that provides a better UI for theme selection.
 * Features a clean layout with theme preview and easy switching.
 */
public class ThemeSelector extends RRVBox {
    private final StringProperty selectedThemeProperty;
    private ComboBox<String> themeComboBox;
    private RRButton previewButton;
    private RRButton downloadButton;

    /**
     * Constructs a ThemeSelector with the current theme loaded from settings.
     */
    public ThemeSelector() {
        this(SettingsHandler.getValue(Settings.THEME));
    }

    /**
     * Constructs a ThemeSelector with the specified current theme.
     *
     * @param currentTheme The currently selected theme.
     */
    public ThemeSelector(String currentTheme) {
        selectedThemeProperty = new SimpleStringProperty(currentTheme);

        setAlignment(Pos.TOP_LEFT);
        getStyleClass().add("theme-selector");

        var contentSection = createContentSection();
        getChildren().add(contentSection);

        loadAvailableThemes();
    }

    private VBox createContentSection() {
        var section = new RRFormSection();
        section.setLocalizedHeaderText("railroad.home.settings.appearance.selecttheme");

        themeComboBox = new ComboBox<>();
        HBox.setHgrow(themeComboBox, Priority.ALWAYS);
        themeComboBox.getStyleClass().add("theme-selector-combo");

        previewButton = new RRButton();
        previewButton.setIcon(FontAwesomeSolid.EYE);
        previewButton.setButtonSize(ButtonSize.SMALL);
        previewButton.setVariant(ButtonVariant.GHOST);
        previewButton.setOnAction(_ -> previewSelectedTheme());

        var selectionRow = new HBox();
        selectionRow.setAlignment(Pos.CENTER_LEFT);
        selectionRow.getStyleClass().add("theme-selector-selection-row");
        selectionRow.getChildren().addAll(themeComboBox, previewButton);

        section.addContent(selectionRow);

        downloadButton = new RRButton("railroad.home.settings.appearance.downloadtheme");
        downloadButton.setIcon(FontAwesomeSolid.DOWNLOAD);
        downloadButton.setVariant(ButtonVariant.PRIMARY);
        downloadButton.setOnAction(_ -> new ThemeDownloadPane(getScene().getWindow()));

        section.addContent(downloadButton);

        return section;
    }

    private void loadAvailableThemes() {
        themeComboBox.getItems().clear();

        themeComboBox.getItems().addAll("default-dark", "default-light");

        List<Path> downloadedThemes = ThemeDownloadManager.getDownloaded();
        for (Path themePath : downloadedThemes) {
            String themeName = themePath.getFileName().toString().replace(".css", "");
            if (!themeComboBox.getItems().contains(themeName)) {
                themeComboBox.getItems().add(themeName);
            }
        }

        themeComboBox.setValue(selectedThemeProperty.get());

        ThemeManager.getCurrentThemeProperty().addListener((_, _, newValue) -> {
            themeComboBox.setValue(newValue);
        });

        themeComboBox.valueProperty().addListener((_, _, newValue) -> {
            selectedThemeProperty.set(newValue);
        });

        selectedThemeProperty.addListener((_, _, newValue) -> {
            applyTheme(newValue);
        });
    }

    private void previewSelectedTheme() {
        String selectedTheme = themeComboBox.getValue();
        if (selectedTheme != null) {
            new ThemeExamplePane(selectedTheme);
        }
    }

    private void applyTheme(String themeName) {
        ThemeManager.setTheme(themeName);
    }

    /**
     * Returns the property representing the selected theme.
     *
     * @return The StringProperty for the selected theme.
     */
    public StringProperty selectedThemeProperty() {
        return selectedThemeProperty;
    }

    /**
     * Gets the currently selected theme.
     *
     * @return The name of the selected theme.
     */
    public String getSelectedTheme() {
        return selectedThemeProperty.get();
    }

    /**
     * Sets the selected theme and updates the ComboBox value.
     *
     * @param theme The name of the theme to select.
     */
    public void setSelectedTheme(String theme) {
        selectedThemeProperty.set(theme);
        themeComboBox.setValue(theme);
    }
}
