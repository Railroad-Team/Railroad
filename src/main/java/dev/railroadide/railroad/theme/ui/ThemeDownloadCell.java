package dev.railroadide.railroad.theme.ui;

import dev.railroadide.railroad.theme.Theme;
import dev.railroadide.railroad.theme.ThemeDownloadManager;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.localized.LocalizedTooltip;
import dev.railroadide.railroad.ui.styling.ButtonSize;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.commons.text.WordUtils;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/**
 * A compact theme row with installation status and preview actions.
 */
public class ThemeDownloadCell extends ListCell<Theme> {
    private final HBox content;
    private final VBox infoSection;
    private final HBox actionSection;
    private final Label themeNameLabel;
    private final Label themeSizeLabel;
    private final RRButton downloadButton;
    private final RRButton previewButton;
    private final ObjectProperty<Theme> themeProperty = new SimpleObjectProperty<>();

    /**
     * Constructs a theme row with download and preview actions.
     * Sets up the visual components including theme information display and download/preview buttons.
     */
    public ThemeDownloadCell() {
        super();
        getStyleClass().add("theme-download-cell");

        setMinWidth(0);
        setPrefWidth(0);
        content = new HBox(12);
        content.setMaxWidth(Double.MAX_VALUE);
        content.setAlignment(Pos.CENTER_LEFT);
        content.getStyleClass().addAll("transparent-background", "theme-download-content");

        infoSection = new VBox(4);
        infoSection.setMinWidth(0);
        infoSection.setAlignment(Pos.CENTER_LEFT);
        infoSection.getStyleClass().addAll("transparent-background", "theme-download-info");
        HBox.setHgrow(infoSection, Priority.ALWAYS);

        themeNameLabel = new Label();
        themeNameLabel.setWrapText(true);
        themeNameLabel.getStyleClass().add("theme-download-name");

        themeSizeLabel = new Label();
        themeSizeLabel.getStyleClass().add("theme-download-size");

        infoSection.getChildren().addAll(themeNameLabel, themeSizeLabel);

        actionSection = new HBox(8);
        actionSection.setMinWidth(USE_PREF_SIZE);
        actionSection.setAlignment(Pos.CENTER_RIGHT);
        actionSection.getStyleClass().addAll("transparent-background", "theme-download-actions");

        previewButton = new RRButton();
        previewButton.setIcon(FontAwesomeSolid.EYE);
        previewButton.setButtonSize(ButtonSize.SMALL);
        previewButton.setVariant(ButtonVariant.GHOST);
        previewButton.getStyleClass().add("theme-dialog-icon");
        previewButton.setMinWidth(30);
        previewButton.setPrefWidth(30);
        previewButton.setTooltip(new LocalizedTooltip("railroad.home.settings.appearance.preview.tooltip"));

        downloadButton = new RRButton("railroad.home.settings.appearance.download");
        downloadButton.setButtonSize(ButtonSize.SMALL);
        downloadButton.setVariant(ButtonVariant.PRIMARY);

        actionSection.getChildren().addAll(previewButton, downloadButton);

        content.getChildren().addAll(infoSection, actionSection);

        setupEventHandlers();
        setupPropertyBindings();
    }

    private void setupEventHandlers() {
        downloadButton.setOnAction(_ -> {
            Theme theme = themeProperty.get();
            if (theme != null) {
                boolean success = ThemeDownloadManager.downloadTheme(theme);
                updateButtonStates(success);
            }
        });

        previewButton.setOnAction(_ -> {
            Theme theme = themeProperty.get();
            if (theme != null) {
                new ThemeExamplePane(theme.getName().replace(".css", ""), getScene().getWindow());
            }
        });
    }

    private void setupPropertyBindings() {
        ObservableValue<String> themeName = themeProperty.map(theme -> WordUtils.capitalize(
            theme.getName()
                .replace("\"", "")
                .replace(".css", "")
                .replace("-", " ")));
        themeNameLabel.textProperty().bind(themeName);

        ObservableValue<String> themeSize = themeProperty.map(theme -> {
            if (theme.getSize() > 0) {
                double sizeKB = theme.getSize() / 1024.0;
                if (sizeKB < 1024)
                    return String.format("%.1f KB", sizeKB);
                else {
                    double sizeMB = sizeKB / 1024.0;
                    return String.format("%.1f MB", sizeMB);
                }
            }
            return "";
        });
        themeSizeLabel.textProperty().bind(themeSize);

        themeProperty.addListener((_, _, newValue) -> {
            if (newValue != null) {
                boolean isDownloaded = ThemeDownloadManager.isDownloaded(newValue);
                updateButtonStates(isDownloaded);
            }
        });
    }

    private void updateButtonStates(boolean isDownloaded) {
        if (isDownloaded) {
            downloadButton.setLocalizedText("railroad.home.settings.appearance.installed");
            downloadButton.setVariant(ButtonVariant.SUCCESS);
            downloadButton.setDisable(true);
            previewButton.setDisable(false);
        } else {
            downloadButton.setLocalizedText("railroad.home.settings.appearance.download");
            downloadButton.setVariant(ButtonVariant.PRIMARY);
            downloadButton.setDisable(false);
            previewButton.setDisable(true);
        }
    }

    @Override
    protected void updateItem(Theme item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            themeProperty.set(null);
        } else {
            themeProperty.set(item);
            setGraphic(content);
        }
    }
}
