package dev.railroadide.railroad.welcome;

import dev.railroadide.railroad.AppResources;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ui.RRListView;
import dev.railroadide.railroad.ui.RRNavigationItem;
import dev.railroadide.railroad.ui.RRSidebar;
import dev.railroadide.railroad.ui.id.UIIds;
import javafx.geometry.Pos;
import javafx.scene.control.ListCell;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import lombok.Getter;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/** Welcome navigation sidebar showing application branding and the available start-screen actions. */
public class WelcomeLeftPane extends RRSidebar {
    /**
     * Navigation list whose selection is handled by the enclosing welcome pane.
     *
     * @return the live menu list
     */
    @Getter
    private final RRListView<MenuType> listView;

    /** Builds the branding and menu list, registering the sidebar's UI identifier while attached. */
    public WelcomeLeftPane() {
        getStyleClass().add("welcome-left-pane");
        setAlignment(Pos.TOP_CENTER);

        var topBox = new VBox();
        topBox.setAlignment(Pos.CENTER);
        topBox.getStyleClass().add("welcome-left-top-box");

        var logo = new ImageView(new Image(AppResources.iconStream(), 80, 80, true, true));
        var appName = new Text(Services.APPLICATION_INFO.getName());
        appName.getStyleClass().add("welcome-app-name");
        var appVersion = new Text(Services.APPLICATION_INFO.getVersion());
        appVersion.getStyleClass().add("welcome-app-version");
        topBox.getChildren().addAll(logo, appName, appVersion);

        listView = new RRListView<>();
        listView.getItems().addAll(MenuType.values());
        listView.setCellFactory(_ -> new MenuTypeCell());
        listView.getStyleClass().add("welcome-left-pane-list");
        listView.setFixedCellSize(44);
        listView.setFocusTraversable(false);
        VBox.setVgrow(listView, Priority.ALWAYS);

        this.setFocusTraversable(false);

        var separator = new Separator();
        separator.getStyleClass().add("welcome-left-separator");

        getChildren().clear();
        getChildren().addAll(topBox, separator, listView);

        Services.UI_MANAGER.assignWhileAttached(UIIds.Welcome.WELCOME_LEFT, this);
    }

    /** Navigation destinations and actions offered by the welcome sidebar. */
    @Getter
    public enum MenuType {
        /** Displays the search header and known projects. */
        HOME("railroad.home.welcome.home", FontAwesomeSolid.HOME),
        /** Displays project-type selection and onboarding. */
        NEW_PROJECT("railroad.home.welcome.newproject",
            FontAwesomeSolid.PLUS),
        /** Opens a directory chooser for an existing project. */
        OPEN_PROJECT("railroad.home.welcome.openproject",
            FontAwesomeSolid.FOLDER_OPEN),
        /** Displays repository browsing and cloning controls. */
        IMPORT_PROJECT("railroad.home.welcome.importproject",
            FontAwesomeSolid.FILE_IMPORT),
        /** Opens the application settings window. */
        SETTINGS("railroad.home.welcome.settings", FontAwesomeSolid.COG);

        /**
         * Translation key for the menu label.
         *
         * @return the label's translation key
         */
        private final String key;
        /**
         * Icon displayed beside the menu label.
         *
         * @return the menu icon
         */
        private final Ikon icon;
        /**
         * Configured menu color, currently unused by the menu cell renderer.
         *
         * @return the configured paint
         */
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

    /** Renders menu entries as localized navigation items with synchronized selection styling. */
    public static class MenuTypeCell extends ListCell<MenuType> {
        /** Creates an empty menu cell. */
        public MenuTypeCell() {
        }

        /**
         * Replaces the navigation graphic for the current menu entry, clearing it for an empty cell.
         *
         * @param item menu entry to display, or null
         * @param empty whether the cell has no item
         */
        @Override
        protected void updateItem(MenuType item, boolean empty) {
            super.updateItem(item, empty);
            setGraphic(null); // Always clear previous graphic
            if (empty || item == null)
                return;

            var navItem = new RRNavigationItem();
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
