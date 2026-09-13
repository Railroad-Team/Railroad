package dev.railroadide.railroad.theme.ui;

import dev.railroadide.railroad.theme.Theme;
import dev.railroadide.railroad.theme.ThemeDownloadManager;
import dev.railroadide.railroad.ui.*;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.ui.localized.LocalizedTooltip;
import dev.railroadide.railroad.ui.styling.ButtonSize;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.window.WindowBuilder;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/**
 * Browses available themes with fixed navigation and footer actions.
 */
public class ThemeDownloadPane {
    private ListView<Theme> themeListView;
    private LocalizedLabel statusLabel;
    private RRButton refreshButton;

    /**
     * Constructs a new ThemeDownloadPane.
     *
     * @param owner The owner window for modality.
     */
    public ThemeDownloadPane(Window owner) {
        var mainContainer = new BorderPane();
        mainContainer.getStyleClass().add("theme-download-pane");

        var headerSection = createHeaderSection();
        mainContainer.setTop(headerSection);

        var themesSection = createThemesSection();
        mainContainer.setCenter(themesSection);

        var footerSection = createFooterSection();
        mainContainer.setBottom(footerSection);

        loadThemes();

        WindowBuilder.create()
            .title("railroad.home.settings.appearance.downloadtheme", true)
            .minSize(560, 420)
            .owner(owner)
            .modality(Modality.WINDOW_MODAL)
            .scene(new Scene(mainContainer, 780, 620))
            .build();
    }

    private VBox createHeaderSection() {
        var headerSection = new VBox(6);
        headerSection.setAlignment(Pos.CENTER_LEFT);
        headerSection.getStyleClass().add("theme-download-header");

        var description = new LocalizedLabel("railroad.home.settings.appearance.downloadtheme.description");
        description.getStyleClass().add("theme-download-description");
        description.setWrapText(true);
        var title = new LocalizedLabel("railroad.home.settings.appearance.downloadtheme");
        title.getStyleClass().add("theme-dialog-title");

        statusLabel = new LocalizedLabel("railroad.home.settings.appearance.loading");
        statusLabel.getStyleClass().add("theme-download-status");

        headerSection.getChildren().addAll(title, description);
        return headerSection;
    }

    private VBox createThemesSection() {
        var themesSection = new VBox(12);
        themesSection.setMinHeight(0);
        themesSection.getStyleClass().add("theme-download-themes-section");
        VBox.setVgrow(themesSection, Priority.ALWAYS);

        var sectionHeader = new HBox(8);
        sectionHeader.setAlignment(Pos.CENTER_LEFT);
        sectionHeader.getStyleClass().add("theme-download-section-header");

        var themesLabel = new LocalizedLabel("railroad.home.settings.appearance.themes");
        themesLabel.getStyleClass().add("theme-download-themes-label");

        refreshButton = new RRButton();
        refreshButton.setIcon(FontAwesomeSolid.SYNC_ALT);
        refreshButton.setButtonSize(ButtonSize.SMALL);
        refreshButton.setVariant(ButtonVariant.GHOST);
        refreshButton.setOnAction(_ -> loadThemes());
        refreshButton.getStyleClass().add("theme-dialog-icon");
        refreshButton.setMinWidth(30);
        refreshButton.setPrefWidth(30);
        refreshButton.setTooltip(new LocalizedTooltip("railroad.generic.refresh"));

        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        sectionHeader.getChildren().addAll(themesLabel, statusLabel, refreshButton);

        themeListView = new ListView<>();
        themeListView.setMinHeight(0);
        var placeholder = new Label();
        placeholder.textProperty().bind(statusLabel.textProperty());
        themeListView.setPlaceholder(placeholder);
        themeListView.setCellFactory(_ -> new ThemeDownloadCell());
        themeListView.getStyleClass().add("theme-download-list-view");
        VBox.setVgrow(themeListView, Priority.ALWAYS);

        themesSection.getChildren().addAll(sectionHeader, themeListView);
        return themesSection;
    }

    private HBox createFooterSection() {
        var footerSection = new HBox(8);
        footerSection.setAlignment(Pos.CENTER_RIGHT);
        footerSection.getStyleClass().add("theme-download-footer");

        var closeButton = new RRButton("railroad.generic.close");
        closeButton.setVariant(ButtonVariant.SECONDARY);
        closeButton.setCancelButton(true);
        closeButton.setOnAction(event -> {
            var target = (Node) event.getTarget();
            var stage = (Stage) target.sceneProperty().get().getWindow();
            stage.close();
        });

        footerSection.getChildren().add(closeButton);
        return footerSection;
    }

    private void loadThemes() {
        statusLabel.setKey("railroad.home.settings.appearance.loading");
        refreshButton.setLoading(true);
        refreshButton.setDisable(true);

        // Load themes in background to avoid blocking UI
        CompletableFuture.supplyAsync(() -> ThemeDownloadManager
            .fetchThemes("https://api.github.com/repos/Railroad-Team/Themes/contents"))
            .whenComplete((themes, error) -> Platform.runLater(() -> {
                themeListView.getItems().clear();

                if (error != null || themes.isEmpty()) {
                    statusLabel.setKey("railroad.home.settings.appearance.notfound");
                } else {
                    statusLabel.setKey("railroad.home.settings.appearance.themes.found", themes.size());
                    themeListView.getItems().addAll(themes);
                }

                refreshButton.setLoading(false);
                refreshButton.setDisable(false);
            }));
    }
}
