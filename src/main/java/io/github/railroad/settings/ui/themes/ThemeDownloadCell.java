package io.github.railroad.settings.ui.themes;

import io.github.railroad.core.ui.RRButton;
import io.github.railroad.core.ui.RRCard;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.gradle.internal.impldep.org.apache.commons.lang.WordUtils;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

/**
 * A modernized theme download cell with improved visual design and user experience.
 * Features a card-based layout with clear action buttons and better information hierarchy.
 */
public class ThemeDownloadCell extends ListCell<Theme> {
    private final RRCard card;
    private final HBox content;
    private final VBox infoSection;
    private final HBox actionSection;
    private final Label themeNameLabel;
    private final Label themeSizeLabel;
    private final RRButton downloadButton;
    private final RRButton previewButton;
    private final ObjectProperty<Theme> themeProperty = new SimpleObjectProperty<>();

    /**
     * Constructs a new ThemeDownloadCell with modern card-based layout and action buttons.
     * Sets up the visual components including theme information display and download/preview buttons.
     */
    public ThemeDownloadCell() {
        super();
        getStyleClass().add("theme-download-cell");

        card = new RRCard(12, new Insets(16));
        card.setInteractive(false);
        card.getStyleClass().add("theme-download-card");

        content = new HBox(16);
        content.setAlignment(Pos.CENTER_LEFT);

        infoSection = new VBox(4);
        infoSection.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(infoSection, Priority.ALWAYS);

        themeNameLabel = new Label();
        themeNameLabel.getStyleClass().add("theme-download-name");

        themeSizeLabel = new Label();
        themeSizeLabel.getStyleClass().add("theme-download-size");

        infoSection.getChildren().addAll(themeNameLabel, themeSizeLabel);

        actionSection = new HBox(8);
        actionSection.setAlignment(Pos.CENTER_RIGHT);

        previewButton = new RRButton();
        previewButton.setIcon(FontAwesomeSolid.EYE);
        previewButton.setButtonSize(RRButton.ButtonSize.SMALL);
        previewButton.setVariant(RRButton.ButtonVariant.GHOST);
        previewButton.setTooltip(new Tooltip("Preview theme"));

        downloadButton = new RRButton("railroad.home.settings.appearance.download");
        downloadButton.setButtonSize(RRButton.ButtonSize.SMALL);
        downloadButton.setVariant(RRButton.ButtonVariant.PRIMARY);

        actionSection.getChildren().addAll(previewButton, downloadButton);

        content.getChildren().addAll(infoSection, actionSection);
        card.addContent(content);

        setupEventHandlers();
        setupPropertyBindings();
    }

    private void setupEventHandlers() {
        downloadButton.setOnAction(e -> {
            Theme theme = themeProperty.get();
            if (theme != null) {
                boolean success = ThemeDownloadManager.downloadTheme(theme);
                updateDownloadButtonState(success);
            }
        });

        previewButton.setOnAction(e -> {
            Theme theme = themeProperty.get();
            if (theme != null) {
                new ThemeExamplePane(theme.getName());
            }
        });
    }

    private void setupPropertyBindings() {
        ObservableValue<String> themeName = themeProperty.map(theme ->
                WordUtils.capitalize(
                        theme.getName()
                                .replace("\"", "")
                                .replace(".css", "")
                                .replace("-", " ")
                ));
        themeNameLabel.textProperty().bind(themeName);

        ObservableValue<String> themeSize = themeProperty.map(theme -> {
            if (theme.getSize() > 0) {
                double sizeKB = theme.getSize() / 1024.0;
                if (sizeKB < 1024) {
                    return String.format("%.1f KB", sizeKB);
                } else {
                    double sizeMB = sizeKB / 1024.0;
                    return String.format("%.1f MB", sizeMB);
                }
            }
            return "";
        });
        themeSizeLabel.textProperty().bind(themeSize);

        themeProperty.addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                boolean isDownloaded = ThemeDownloadManager.isDownloaded(newValue);
                updateDownloadButtonState(isDownloaded);
            }
        });
    }

    private void updateDownloadButtonState(boolean isDownloaded) {
        if (isDownloaded) {
            downloadButton.setLocalizedText("railroad.home.settings.appearance.installed");
            downloadButton.setVariant(RRButton.ButtonVariant.SUCCESS);
            downloadButton.setDisable(true);
        } else {
            downloadButton.setLocalizedText("railroad.home.settings.appearance.download");
            downloadButton.setVariant(RRButton.ButtonVariant.PRIMARY);
            downloadButton.setDisable(false);
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
            setGraphic(card);
        }
    }
} 