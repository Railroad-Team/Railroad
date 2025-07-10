package io.github.railroad.welcome;

import io.github.railroad.Railroad;
import io.github.railroad.core.ui.RRListView;
import io.github.railroad.core.ui.RRNavigationItem;
import io.github.railroad.core.ui.RRSidebar;
import io.github.railroad.core.ui.localized.LocalizedLabel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ListCell;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import lombok.Getter;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

public class WelcomeLeftPane extends RRSidebar {
    @Getter
    private final RRListView<MenuType> listView;

    public WelcomeLeftPane() {
        setPadding(new Insets(18, 8, 18, 8));
        getStyleClass().add("welcome-left-pane");
        setMinWidth(220);
        setMaxWidth(240);
        setAlignment(Pos.TOP_CENTER);
        setSpacing(18);

        var topBox = new VBox(6);
        topBox.setAlignment(Pos.CENTER);
        topBox.setPadding(new Insets(0, 0, 8, 0));
        topBox.setPrefHeight(110);

        var logo = new ImageView(new Image(Railroad.getResourceAsStream("images/logo.png"), 80, 80, true, true));
        var appName = new LocalizedLabel("railroad.app.name");
        appName.getStyleClass().add("welcome-app-name");
        var appVersion = new LocalizedLabel("railroad.app.version");
        appVersion.getStyleClass().add("welcome-app-version");
        topBox.getChildren().addAll(logo, appName, appVersion);

        listView = new RRListView<>();
        listView.getItems().addAll(MenuType.values());
        listView.setCellFactory(param -> new MenuTypeCell());
        listView.getStyleClass().add("welcome-left-pane-list");
        listView.setPrefHeight(320);
        listView.setFixedCellSize(44);
        listView.setFocusTraversable(false);
        VBox.setVgrow(listView, Priority.ALWAYS);

        this.setFocusTraversable(false);

        var separator = new Separator();
        separator.setPadding(new Insets(10, -10, 10, -10));

        getChildren().clear();
        getChildren().addAll(topBox, separator, listView);
    }

    @Getter
    public enum MenuType {
        HOME("railroad.home.welcome.home", FontAwesomeSolid.HOME),
        NEW_PROJECT("railroad.home.welcome.newproject", FontAwesomeSolid.PLUS),
        OPEN_PROJECT("railroad.home.welcome.openproject", FontAwesomeSolid.FOLDER_OPEN),
        IMPORT_PROJECT("railroad.home.welcome.importproject", FontAwesomeSolid.FILE_IMPORT),
        SETTINGS("railroad.home.welcome.settings", FontAwesomeSolid.COG);

        private final String key;
        private final Ikon icon;
        private final Paint color;

        MenuType(String key, Ikon icon, Paint color) {
            this.key = key;
            this.icon = icon;
            this.color = color;
        }

        MenuType(String key, Ikon icon) {
            this(key, icon, Color.WHITE);
        }
    }

    public static class MenuTypeCell extends ListCell<MenuType> {
        @Override
        protected void updateItem(MenuType item, boolean empty) {
            super.updateItem(item, empty);
            setGraphic(null); // Always clear previous graphic
            if (empty || item == null)
                return;

            var navItem = new RRNavigationItem();
            navItem.setPrefHeight(44);
            navItem.getStyleClass().add("welcome-nav-item");
            navItem.setIcon(item.getIcon());
            navItem.setLocalizedText(item.getKey());
            navItem.setSelected(isSelected());
            setGraphic(navItem);
        }

        /**
         * Updates the selected state of the navigation item when the cell selection changes.
         * 
         * @param selected true if the cell is selected, false otherwise
         */
        @Override
        public void updateSelected(boolean selected) {
            super.updateSelected(selected);
            if (getGraphic() instanceof RRNavigationItem navItem) {
                navItem.setSelected(selected);
            }
        }
    }
}