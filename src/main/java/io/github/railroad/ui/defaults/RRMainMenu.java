package io.github.railroad.ui.defaults;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.VBox;

public class RRMainMenu extends VBox {
    public RRMainMenu() {
        super();
        MenuBar menubar = new MenuBar();
        Menu FileMenu = new Menu("File");
        MenuItem filemenu1 = new MenuItem("new");
        MenuItem filemenu2 = new MenuItem("Save");
        MenuItem filemenu3 = new MenuItem("Exit");
        Menu EditMenu = new Menu("Edit");
        MenuItem EditMenu1 = new MenuItem("Cut");
        MenuItem EditMenu2 = new MenuItem("Copy");
        MenuItem EditMenu3 = new MenuItem("Paste");
        EditMenu.getItems().addAll(EditMenu1, EditMenu2, EditMenu3);
        FileMenu.getItems().addAll(filemenu1, filemenu2, filemenu3);
        getChildren().add(menubar);

    }
}
