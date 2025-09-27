package dev.railroadide.railroad.window;

import dev.railroadide.railroad.AppResources;
import dev.railroadide.railroad.theme.ThemeManager;
import dev.railroadide.railroad.utility.MacUtils;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.InputStream;
import java.util.function.Consumer;

public class WindowBuilder {
    private String title = "";
    private Scene scene;
    private InputStream iconStream = AppResources.iconStream();
    private Window owner;
    private Modality modality = Modality.NONE;
    private boolean resizable = true;
    private boolean maximized = false;
    private boolean showImmediately = true;
    private Consumer<Stage> onInit;

    public static WindowBuilder create() {
        return new WindowBuilder();
    }

    public WindowBuilder title(String title) {
        this.title = title;
        return this;
    }

    public WindowBuilder scene(Scene scene) {
        this.scene = scene;
        ThemeManager.apply(scene);
        return this;
    }

    public WindowBuilder icon(InputStream iconStream) {
        this.iconStream = iconStream;
        return this;
    }

    public WindowBuilder owner(Window owner) {
        this.owner = owner;
        return this;
    }

    public WindowBuilder modality(Modality modality) {
        this.modality = modality;
        return this;
    }

    public WindowBuilder resizable(boolean resizable) {
        this.resizable = resizable;
        return this;
    }

    public WindowBuilder maximized(boolean maximized) {
        this.maximized = maximized;
        return this;
    }

    public WindowBuilder showImmediately(boolean show) {
        this.showImmediately = show;
        return this;
    }

    public WindowBuilder onInit(Consumer<Stage> consumer) {
        this.onInit = consumer;
        return this;
    }

    /**
     * Build and return the configured Stage.
     */
    public Stage build() {
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.setResizable(resizable);
        stage.setMaximized(maximized);

        if (scene != null) {
            stage.setScene(scene);
        }

        if (iconStream != null) {
            stage.getIcons().add(new Image(iconStream));
        }

        if (owner != null) {
            stage.initOwner(owner);
        }

        stage.initModality(modality);

        if (onInit != null) {
            onInit.accept(stage);
        }

        // Create a MacOS specific Menu Bar and Application Menu
        MacUtils.initialize();

        stage.show();

        // Show the MacOS specific menu bar
        MacUtils.show(stage);

        if (showImmediately && (modality == Modality.APPLICATION_MODAL || modality == Modality.WINDOW_MODAL)) {
            stage.showAndWait(); // TODO: Confirm that this is actually possible to do.
        }

        return stage;
    }
}
