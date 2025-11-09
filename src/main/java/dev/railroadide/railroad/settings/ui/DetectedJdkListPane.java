package dev.railroadide.railroad.settings.ui;

import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRListView;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.core.ui.localized.LocalizedTooltip;
import dev.railroadide.railroad.AppResources;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.JDKManager;
import dev.railroadide.railroad.localization.L18n;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome6.FontAwesomeBrands;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays the JDKs detected by {@link JDKManager} with a quick refresh action.
 */
public class DetectedJdkListPane extends RRVBox {
    private final ObservableList<JDK> items = FXCollections.observableArrayList();
    private final ListView<JDK> listView = new RRListView<>(items);
    private final LocalizedLabel countLabel = new LocalizedLabel("railroad.settings.ide.jdk_management.detected.count", 0);

    public DetectedJdkListPane() {
        setSpacing(12);
        setFillWidth(true);
        getStyleClass().add("detected-jdk-list-pane");

        var header = new RRHBox(8);
        header.setFillHeight(true);
        var title = new LocalizedLabel("railroad.settings.ide.jdk_management.detected.title");
        title.getStyleClass().add("section-label");

        var refreshButton = new RRButton(null, FontAwesomeSolid.SYNC);
        refreshButton.setButtonSize(RRButton.ButtonSize.SMALL);
        refreshButton.setVariant(RRButton.ButtonVariant.SECONDARY);
        refreshButton.setTooltip(new LocalizedTooltip("railroad.settings.ide.jdk_management.detected.refresh.tooltip"));
        refreshButton.setOnAction($ -> refresh());

        header.getChildren().addAll(title, refreshButton);

        listView.setItems(items);
        listView.setFocusTraversable(false);
        listView.setMinHeight(160);
        listView.setPrefHeight(220);
        listView.setMaxHeight(220);
        listView.setCellFactory(view -> new JdkCell());
        listView.setPlaceholder(new LocalizedLabel("railroad.settings.ide.jdk_management.detected.empty"));

        getChildren().addAll(header, listView, countLabel);

        refresh();
    }

    private void refresh() {
        JDKManager.refreshJDKs();
        List<JDK> detected = JDKManager.getAvailableJDKs();
        var copy = new ArrayList<>(detected);
        items.setAll(copy);
        countLabel.setKey("railroad.settings.ide.jdk_management.detected.count", detected.size());
    }

    public static class JdkCell extends ListCell<JDK> {
        private final HBox container = new HBox(12);
        private final VBox textContainer = new VBox(2);
        private final LocalizedLabel versionLabel = new LocalizedLabel("");
        private final LocalizedLabel nameLabel = new LocalizedLabel("");
        private final LocalizedLabel pathLabel = new LocalizedLabel("");

        public JdkCell() {
            container.setAlignment(Pos.CENTER_LEFT);
            container.getStyleClass().add("detected-jdk-cell");

            versionLabel.getStyleClass().add("detected-jdk-version");
            nameLabel.getStyleClass().add("detected-jdk-name");
            pathLabel.getStyleClass().add("detected-jdk-path");

            textContainer.getChildren().addAll(versionLabel, nameLabel, pathLabel);
            HBox.setHgrow(textContainer, Priority.ALWAYS);
        }

        @Override
        protected void updateItem(JDK item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }

            JDK.Brand brand = item.brand();
            Node iconView = null;
            if (brand.isImage()) {
                try {
                    var image = new Image(AppResources.getResourceAsStream(brand.getImagePath()), 20, 20, true, true);
                    iconView = new ImageView(image);
                } catch (Exception exception) {
                    Railroad.LOGGER.error("Failed to load image icon for JDK brand {}", brand.name(), exception);
                }
            } else if (brand.isIkon()) {
                var icon = new FontIcon(brand.getIcon());
                icon.setIconSize(20);
                iconView = icon;
            }

            if (iconView == null) {
                Railroad.LOGGER.warn("No icon found for JDK brand {}, using default Java icon", brand.name());
                iconView = new FontIcon(FontAwesomeBrands.JAVA);
                ((FontIcon) iconView).setIconSize(20);
            }

            iconView.getStyleClass().add("detected-jdk-icon");

            String version = item.version() != null
                ? item.version().toReleaseString()
                : L18n.localize("railroad.settings.ide.jdk_management.detected.unknown_version");
            if (version.endsWith(".0") && version.length() > 2) {
                version = version.substring(0, version.length() - 2);
            }
            versionLabel.setKey("railroad.settings.ide.jdk_management.detected.version", version);

            String name = item.name();
            nameLabel.setKey("railroad.settings.ide.jdk_management.detected.name", name);

            pathLabel.setKey("railroad.settings.ide.jdk_management.detected.path", item.path());

            container.getChildren().setAll(iconView, textContainer);
            setGraphic(container);
            setText(null);
        }
    }
}
