package dev.railroadide.railroad.theme.ui;

import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRFormContainer;
import dev.railroadide.core.ui.RRListView;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.theme.Theme;
import dev.railroadide.railroad.theme.ThemeDownloadManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.util.List;

/**
 * A modernized theme download pane with improved UI/UX design.
 * Features a clean, card-based layout with better visual hierarchy and modern styling.
 */
public class ThemeDownloadPane {
    private final Stage stage;
    private ListView<Theme> themeListView;
    private LocalizedLabel statusLabel;
    private RRButton refreshButton;
    private RRButton closeButton;

    public ThemeDownloadPane() {
        stage = new Stage();
        stage.setTitle(L18n.localize("railroad.home.settings.appearance.downloadtheme"));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(true);
        stage.setMinWidth(600);
        stage.setMinHeight(500);

        var mainContainer = new RRFormContainer();
        mainContainer.setLocalizedTitle("railroad.home.settings.appearance.downloadtheme");
        mainContainer.setPadding(new Insets(24));

        var headerSection = createHeaderSection();
        mainContainer.addContent(headerSection);

        var themesSection = createThemesSection();
        mainContainer.addContent(themesSection);

        var footerSection = createFooterSection();
        mainContainer.addContent(footerSection);

        var scene = new Scene(mainContainer, 700, 600);
        stage.setScene(scene);

        loadThemes();

        stage.show();
    }

    private VBox createHeaderSection() {
        var headerSection = new VBox(16);
        headerSection.setAlignment(Pos.CENTER_LEFT);

        var description = new LocalizedLabel("railroad.home.settings.appearance.downloadtheme.description");
        description.getStyleClass().add("theme-download-description");

        statusLabel = new LocalizedLabel("railroad.home.settings.appearance.loading");
        statusLabel.getStyleClass().add("theme-download-status");

        headerSection.getChildren().addAll(description, statusLabel);
        return headerSection;
    }

    private VBox createThemesSection() {
        var themesSection = new VBox(12);
        VBox.setVgrow(themesSection, Priority.ALWAYS);

        var sectionHeader = new HBox(12);
        sectionHeader.setAlignment(Pos.CENTER_LEFT);

        var themesLabel = new LocalizedLabel("railroad.home.settings.appearance.themes");
        themesLabel.getStyleClass().add("theme-download-themes-label");

        refreshButton = new RRButton();
        refreshButton.setIcon(FontAwesomeSolid.SYNC_ALT);
        refreshButton.setButtonSize(RRButton.ButtonSize.SMALL);
        refreshButton.setVariant(RRButton.ButtonVariant.GHOST);
        refreshButton.setOnAction(e -> loadThemes());

        sectionHeader.getChildren().addAll(themesLabel, refreshButton);

        themeListView = new RRListView<>();
        themeListView.setCellFactory(param -> new ThemeDownloadCell());
        themeListView.getStyleClass().add("theme-download-list-view");
        VBox.setVgrow(themeListView, Priority.ALWAYS);

        themesSection.getChildren().addAll(sectionHeader, themeListView);
        return themesSection;
    }

    private HBox createFooterSection() {
        var footerSection = new HBox(12);
        footerSection.setAlignment(Pos.CENTER_RIGHT);
        footerSection.setPadding(new Insets(16, 0, 0, 0));

        closeButton = new RRButton("railroad.generic.close");
        closeButton.setVariant(RRButton.ButtonVariant.SECONDARY);
        closeButton.setOnAction(e -> stage.close());

        footerSection.getChildren().add(closeButton);
        return footerSection;
    }

    private void loadThemes() {
        statusLabel.setKey("railroad.home.settings.appearance.loading");
        refreshButton.setLoading(true);

        // Load themes in background to avoid blocking UI
        new Thread(() -> {
            List<Theme> themes = ThemeDownloadManager.fetchThemes("https://api.github.com/repos/Railroad-Team/Themes/contents");

            Platform.runLater(() -> {
                themeListView.getItems().clear();

                if (themes.isEmpty()) {
                    statusLabel.setKey("railroad.home.settings.appearance.notfound");
                    themeListView.setVisible(false);
                } else {
                    statusLabel.setKey("railroad.home.settings.appearance.themes.found", themes.size());
                    themeListView.getItems().addAll(themes);
                    themeListView.setVisible(true);
                }

                refreshButton.setLoading(false);
            });
        }).start();
    }
}
