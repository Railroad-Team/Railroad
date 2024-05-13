package io.github.railroad.settings.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Screen;

public class SettingsAppearancePane extends HBox {
    private final ComboBox screenSelect = new ComboBox();
    private final Label selectScreen = new Label("Select Screen");

    public SettingsAppearancePane() {
        super();

        ObservableList<String> screenSizes = FXCollections.observableArrayList();

        for (Screen screen : Screen.getScreens()){
            var currLength = screenSizes.size();

            String string = String.format("%s: %1s, %2s", currLength + 1, screen.getBounds().getWidth(), screen.getBounds().getHeight());

            screenSizes.add(string);
        }

        screenSelect.setItems(screenSizes);

        getChildren().addAll(selectScreen, screenSelect);
    }
}
