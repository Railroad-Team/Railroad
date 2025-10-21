package dev.railroadide.railroad.window;

import dev.railroadide.railroad.AppResources;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.theme.ThemeManager;
import dev.railroadide.railroad.utility.MacUtils;
import dev.railroadide.railroad.utility.StringUtils;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.io.InputStream;
import java.util.Objects;
import java.util.function.Consumer;

public class WindowBuilder {
    private StageStyle stageStyle = StageStyle.DECORATED;
    private String title = "";
    private boolean translateTitle = false;
    private Scene scene;
    private InputStream iconStream = AppResources.iconStream();
    private Window owner;
    private Modality modality = Modality.NONE;
    private boolean resizable = true;
    private boolean maximized = false;
    private boolean shouldBlockOwnerWindow = true;
    private Consumer<Stage> onInit;
    private double minWidth = -1, minHeight = -1;
    private double width = -1, height = -1;
    private double maxWidth = -1, maxHeight = -1;

    public static WindowBuilder create() {
        return new WindowBuilder();
    }

    public static WindowBuilder createAlert(AlertType alertType, String title, String subtitle, String content) {
        return createAlert(alertType, title, subtitle, content, null);
    }

    public static WindowBuilder createAlert(AlertType alertType, String title, String subtitle, String content, Runnable onClose) {
        return createAlert(alertType, title, subtitle, content, null, onClose);
    }

    public static WindowBuilder createAlert(AlertType alertType, String title, String subtitle, String content, Consumer<AlertBuilder<?>> alertModifier, Runnable onClose) {
        AlertBuilder<?> alertBuilder = AlertBuilder.create()
            .alertType(alertType)
            .title(subtitle)
            .content(content)
            .onClose(onClose);
        if (alertModifier != null) {
            alertModifier.accept(alertBuilder);
        }

        return createAlert(title, alertBuilder);
    }

    public static WindowBuilder createAlert(String title, AlertBuilder<?> alertBuilder) {
        return WindowBuilder.create()
            .title(title, true)
            .owner(Railroad.WINDOW_MANAGER.getPrimaryStage())
            .resizable(false)
            .shouldBlockOwnerWindow(true)
            .stageStyle(StageStyle.UTILITY)
            .scene(alertBuilder.buildScene());
    }

    public static Stage createExceptionAlert(String title, String subtitle, Throwable exception, Runnable onClose) {
        Railroad.LOGGER.error("An exception occurred", exception);
        return createAlert(
            AlertType.ERROR,
            title,
            subtitle,
            StringUtils.exceptionToString(exception),
            alertBuilder -> alertBuilder.translateContent(false),
            onClose
        ).build();
    }

    public static Stage createDialog(String title, String subtitle, String content, Runnable onConfirm, Runnable onCancel) {
        return createDialog(title, subtitle, content, dialogBuilder -> {
            dialogBuilder.onConfirm(onConfirm);
            dialogBuilder.onCancel(onCancel);
        });
    }

    public static Stage createDialog(String title, String subtitle, String content, Consumer<DialogBuilder> dialogModifier) {
        DialogBuilder dialogBuilder = DialogBuilder.create()
            .title(subtitle)
            .content(content);
        if (dialogModifier != null) {
            dialogModifier.accept(dialogBuilder);
        }

        return WindowBuilder.create()
            .title(title, true)
            .owner(Railroad.WINDOW_MANAGER.getPrimaryStage())
            .resizable(false)
            .shouldBlockOwnerWindow(true)
            .stageStyle(StageStyle.UTILITY)
            .scene(dialogBuilder.buildScene())
            .build();
    }

    public WindowBuilder stageStyle(StageStyle stageStyle) {
        this.stageStyle = stageStyle;
        return this;
    }

    public WindowBuilder title(String title) {
        this.title = title;
        return this;
    }

    public WindowBuilder title(String title, boolean translate) {
        this.title = title;
        this.translateTitle = translate;
        return this;
    }

    public WindowBuilder translateTitle(boolean translate) {
        this.translateTitle = translate;
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

    public WindowBuilder shouldBlockOwnerWindow(boolean shouldBlockOwnerWindow) {
        this.shouldBlockOwnerWindow = shouldBlockOwnerWindow;
        return this;
    }

    public WindowBuilder onInit(Consumer<Stage> consumer) {
        this.onInit = consumer;
        return this;
    }

    public WindowBuilder minWidth(double minWidth) {
        this.minWidth = minWidth;
        return this;
    }

    public WindowBuilder minHeight(double minHeight) {
        this.minHeight = minHeight;
        return this;
    }

    public WindowBuilder minSize(double minWidth, double minHeight) {
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        return this;
    }

    public WindowBuilder width(double width) {
        this.width = width;
        return this;
    }

    public WindowBuilder height(double height) {
        this.height = height;
        return this;
    }

    public WindowBuilder size(double width, double height) {
        this.width = width;
        this.height = height;
        return this;
    }

    public WindowBuilder maxWidth(double maxWidth) {
        this.maxWidth = maxWidth;
        return this;
    }

    public WindowBuilder maxHeight(double maxHeight) {
        this.maxHeight = maxHeight;
        return this;
    }

    public WindowBuilder maxSize(double maxWidth, double maxHeight) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        return this;
    }

    /**
     * Build and return the configured Stage.
     */
    public Stage build() {
        var stage = new Stage(stageStyle);
        stage.setTitle(translateTitle ? L18n.localize(title) : title);
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

        if (minWidth >= 0) stage.setMinWidth(minWidth);
        if (minHeight >= 0) stage.setMinHeight(minHeight);
        if (width >= 0) stage.setWidth(width);
        if (height >= 0) stage.setHeight(height);
        if (maxWidth >= 0) stage.setMaxWidth(maxWidth);
        if (maxHeight >= 0) stage.setMaxHeight(maxHeight);

        if (onInit != null) {
            onInit.accept(stage);
        }

        // Create a MacOS specific Menu Bar and Application Menu
        MacUtils.initialize();

        stage.show();

        // Show the MacOS specific menu bar
        MacUtils.show(stage);

        if (Objects.equals(Railroad.WINDOW_MANAGER.getPrimaryStage(), owner)) {
            Railroad.WINDOW_MANAGER.registerChildWindow(stage);
        }

        if (shouldBlockOwnerWindow && (modality == Modality.APPLICATION_MODAL || modality == Modality.WINDOW_MODAL)) {
            stage.showAndWait(); // TODO: Confirm that this is actually possible to do.
        }

        return stage;
    }
}
