package dev.railroadide.railroad.theme.ui;

import dev.railroadide.core.ui.*;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.theme.Theme;
import dev.railroadide.railroad.theme.ThemeDownloadManager;
import dev.railroadide.railroad.window.WindowBuilder;
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
import javafx.stage.Window;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A modernized theme download pane with improved UI/UX design.
 * Features a clean, card-based layout with better visual hierarchy and modern styling.
 */
public class ThemeDownloadPane {
    private final Stage stage;
    private ListView<Theme> themeListView;
    private LocalizedLabel statusLabel;
    private RRButton refreshButton;

    public ThemeDownloadPane(Window owner) {
        var mainContainer = new RRFormContainer();
        mainContainer.setLocalizedTitle("railroad.home.settings.appearance.downloadtheme");
        mainContainer.setPadding(new Insets(24));

        var headerSection = createHeaderSection();
        mainContainer.addContent(headerSection);

        var themesSection = createThemesSection();
        mainContainer.addContent(themesSection);

        var footerSection = createFooterSection();
        mainContainer.addContent(footerSection);

        loadThemes();

        stage = WindowBuilder.create()
            .title("railroad.home.settings.appearance.downloadtheme", true)
            .minSize(690, 590)
            .owner(owner)
            .modality(Modality.APPLICATION_MODAL)
            .scene(new Scene(mainContainer, 700, 600))
            .build();
    }

    private VBox createHeaderSection() {
        var headerSection = new RRVBox(16);
        headerSection.setAlignment(Pos.CENTER_LEFT);

        var description = new LocalizedLabel("railroad.home.settings.appearance.downloadtheme.description");
        description.getStyleClass().add("theme-download-description");

        statusLabel = new LocalizedLabel("railroad.home.settings.appearance.loading");
        statusLabel.getStyleClass().add("theme-download-status");

        headerSection.getChildren().addAll(description, statusLabel);
        return headerSection;
    }

    private VBox createThemesSection() {
        var themesSection = new RRVBox(12);
        VBox.setVgrow(themesSection, Priority.ALWAYS);

        var sectionHeader = new RRHBox(12);
        sectionHeader.setAlignment(Pos.CENTER_LEFT);

        var themesLabel = new LocalizedLabel("railroad.home.settings.appearance.themes");
        themesLabel.getStyleClass().add("theme-download-themes-label");

        refreshButton = new RRButton();
        refreshButton.setIcon(FontAwesomeSolid.SYNC_ALT);
        refreshButton.setButtonSize(RRButton.ButtonSize.SMALL);
        refreshButton.setVariant(RRButton.ButtonVariant.GHOST);
        refreshButton.setOnAction($ -> loadThemes());

        sectionHeader.getChildren().addAll(themesLabel, refreshButton);

        themeListView = new RRListView<>();
        themeListView.setCellFactory(param -> new ThemeDownloadCell());
        themeListView.getStyleClass().add("theme-download-list-view");
        VBox.setVgrow(themeListView, Priority.ALWAYS);

        themesSection.getChildren().addAll(sectionHeader, themeListView);
        return themesSection;
    }

    private HBox createFooterSection() {
        var footerSection = new RRHBox(12);
        footerSection.setAlignment(Pos.CENTER_RIGHT);
        footerSection.setPadding(new Insets(16, 0, 0, 0));

        var closeButton = new RRButton("railroad.generic.close");
        closeButton.setVariant(RRButton.ButtonVariant.SECONDARY);
        closeButton.setOnAction($ -> stage.close());

        footerSection.getChildren().add(closeButton);
        return footerSection;
    }

    private void loadThemes() {
        statusLabel.setKey("railroad.home.settings.appearance.loading");
        refreshButton.setLoading(true);

        // Load themes in background to avoid blocking UI
        CompletableFuture.runAsync(() -> {
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
        });
    }
}
