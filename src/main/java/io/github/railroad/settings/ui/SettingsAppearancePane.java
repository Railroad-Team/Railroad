package io.github.railroad.settings.ui;

import com.google.gson.JsonObject;
import io.github.railroad.Railroad;
import io.github.railroad.utility.ConfigHandler;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventType;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class SettingsAppearancePane extends VBox {
    private final ComboBox screenSelect = new ComboBox();
    private final Label selectScreen = new Label("Select Screen");

    private static void resizeScreen(Rectangle2D bounds, Stage stage) {
        double windowW = Math.max(500, Math.min(bounds.getWidth() * 0.75, 1024));
        double windowH = Math.max(500, Math.min(bounds.getHeight() * 0.75, 768));

        stage.setWidth(windowW);
        stage.setHeight(windowH);
    }

    public static void changeScreen(Rectangle2D bounds, Stage stage, Number screen) {
        //Move to different monitor/screen
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());

        //Center on that monitor/screen
        resizeScreen(bounds, stage);
        stage.centerOnScreen();

        JsonObject config = ConfigHandler.getConfigJson();
        config.get("settings").getAsJsonObject().addProperty("defaultScreen", screen);

        ConfigHandler.updateConfig(config);
    }

    public SettingsAppearancePane() {
        super();

        screenSelect.setPromptText("Select a monitor");

        ObservableList<String> screenSizes = FXCollections.observableArrayList();
        ObservableList<Rectangle2D> screenBounds = FXCollections.observableArrayList();

        for (Screen screen : Screen.getScreens()){
            var currLength = screenSizes.size();

            String string = String.format("%s: %1s, %2s", currLength, screen.getBounds().getWidth() * screen.getOutputScaleX(), screen.getBounds().getHeight() * screen.getOutputScaleY());

            screenSizes.add(string);
            screenBounds.add(screen.getBounds());
        }

        screenSelect.setItems(screenSizes);

        screenSelect.setOnAction(event -> {
            var selected = screenSelect.getValue();
            var selectedNum = Integer.parseInt(String.valueOf(selected.toString().charAt(0)));
            var selectedBounds = screenBounds.get(selectedNum);

            changeScreen(selectedBounds, Railroad.getWindow(), selectedNum);
        });

        getChildren().addAll(selectScreen, screenSelect);
    }
}
