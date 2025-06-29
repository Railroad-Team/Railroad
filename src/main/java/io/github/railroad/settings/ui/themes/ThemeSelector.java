package io.github.railroad.settings.ui.themes;

import io.github.railroad.Railroad;
import io.github.railroad.ui.nodes.RRButton;
import io.github.railroad.ui.nodes.RRFormSection;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.nio.file.Path;
import java.util.List;

/**
 * A modern theme selector component that provides a better UI for theme selection.
 * Features a clean layout with theme preview and easy switching.
 */
public class ThemeSelector extends VBox {
    private ComboBox<String> themeComboBox;
    private RRButton previewButton;
    private RRButton downloadButton;
    private final ObjectProperty<String> selectedThemeProperty;

    public ThemeSelector() {
        this(Railroad.SETTINGS_HANDLER.getStringSetting("railroad:theme"));
    }

    public ThemeSelector(String currentTheme) {
        selectedThemeProperty = new SimpleObjectProperty<>(currentTheme);
        
        setSpacing(16);
        setAlignment(Pos.TOP_LEFT);

        var contentSection = createContentSection();
        getChildren().add(contentSection);

        loadAvailableThemes();
    }

    private VBox createContentSection() {
        var section = new RRFormSection();
        section.setLocalizedHeaderText("railroad.home.settings.appearance.selecttheme");

        themeComboBox = new ComboBox<>();
        themeComboBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(themeComboBox, Priority.ALWAYS);
        themeComboBox.getStyleClass().add("theme-selector-combo");

        previewButton = new RRButton();
        previewButton.setIcon(FontAwesomeSolid.EYE);
        previewButton.setButtonSize(RRButton.ButtonSize.SMALL);
        previewButton.setVariant(RRButton.ButtonVariant.GHOST);
        previewButton.setOnAction(e -> previewSelectedTheme());

        var selectionRow = new HBox(12);
        selectionRow.setAlignment(Pos.CENTER_LEFT);
        selectionRow.getChildren().addAll(themeComboBox, previewButton);

        section.addContent(selectionRow);

        downloadButton = new RRButton("railroad.home.settings.appearance.downloadtheme");
        downloadButton.setIcon(FontAwesomeSolid.DOWNLOAD);
        downloadButton.setVariant(RRButton.ButtonVariant.PRIMARY);
        downloadButton.setOnAction(e -> new ThemeDownloadPane());

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

        themeComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.equals(oldValue)) {
                selectedThemeProperty.set(newValue);
                applyTheme(newValue);
            }
        });
    }

    private void previewSelectedTheme() {
        String selectedTheme = themeComboBox.getValue();
        if (selectedTheme != null) {
            new ThemeExamplePane(selectedTheme + ".css");
        }
    }

    private void applyTheme(String themeName) {
        Railroad.updateTheme(themeName);
    }

    public ObjectProperty<String> selectedThemeProperty() {
        return selectedThemeProperty;
    }

    public String getSelectedTheme() {
        return selectedThemeProperty.get();
    }

    public void setSelectedTheme(String theme) {
        selectedThemeProperty.set(theme);
        themeComboBox.setValue(theme);
    }
}