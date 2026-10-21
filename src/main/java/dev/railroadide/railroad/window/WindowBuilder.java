package dev.railroadide.railroad.window;

import dev.railroadide.railroad.AppResources;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
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
import javafx.stage.WindowEvent;
import org.joml.Matrix3x2d;

import java.io.InputStream;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Configures and shows application stages, including alert and dialog windows.
 * Defaults to a decorated, resizable, non-modal window with the application icon.
 */
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

    /**
     * Creates a window builder with default settings.
     *
     * @return a new window builder
     */
    public static WindowBuilder create() {
        return new WindowBuilder();
    }

    /**
     * Creates a builder for an alert owned by the primary window, with translated text.
     *
     * @param alertType the alert severity
     * @param title the window title translation key
     * @param subtitle the alert heading translation key
     * @param content the message translation key
     * @return the configured window builder
     */
    public static WindowBuilder createAlert(AlertType alertType, String title, String subtitle, String content) {
        return createAlert(alertType, title, subtitle, content, null);
    }

    /**
     * Creates a builder for a translated alert with a dismissal callback.
     *
     * @param alertType the alert severity
     * @param title the window title translation key
     * @param subtitle the alert heading translation key
     * @param content the message translation key
     * @param onClose the dismissal callback, or null for no action
     * @return the configured window builder
     */
    public static WindowBuilder createAlert(
        AlertType alertType,
        String title,
        String subtitle,
        String content,
        Runnable onClose
    ) {
        return createAlert(alertType, title, subtitle, content, null, onClose);
    }

    /**
     * Creates an alert window builder after applying optional alert customization.
     * The supplied text is translated unless the modifier changes that setting.
     *
     * @param alertType the alert severity
     * @param title the window title translation key
     * @param subtitle the alert heading translation key
     * @param content the message translation key
     * @param alertModifier the customization applied before building the scene, or null for none
     * @param onClose the dismissal callback, or null for no action
     * @return the configured window builder
     */
    public static WindowBuilder createAlert(
        AlertType alertType,
        String title,
        String subtitle,
        String content,
        Consumer<AlertBuilder<?>> alertModifier,
        Runnable onClose
    ) {
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

    /**
     * Builds an alert scene and configures a non-resizable utility window owned by the primary stage.
     *
     * @param title the window title translation key
     * @param alertBuilder the builder used to create the alert scene
     * @return the configured window builder, ready to be shown with {@link #build()}
     */
    public static WindowBuilder createAlert(String title, AlertBuilder<?> alertBuilder) {
        return WindowBuilder.create()
            .title(title, true)
            .owner(Railroad.WINDOW_MANAGER.getPrimaryStage())
            .resizable(false)
            .shouldBlockOwnerWindow(true)
            .stageStyle(StageStyle.UTILITY)
            .scene(alertBuilder.buildScene());
    }

    /**
     * Logs an exception and shows an error alert containing its unlocalized formatted details.
     *
     * @param title the window title translation key
     * @param subtitle the alert heading translation key
     * @param exception the exception to log and display
     * @param onClose the dismissal callback, or null for no action
     * @return the shown error stage
     */
    public static Stage createExceptionAlert(String title, String subtitle, Throwable exception, Runnable onClose) {
        Railroad.LOGGER.error("An exception occurred", exception);
        return createAlert(
            AlertType.ERROR,
            title,
            subtitle,
            StringUtils.exceptionToString(exception),
            alertBuilder -> alertBuilder.translateContent(false),
            onClose).build();
    }

    /**
     * Shows a translated dialog with confirmation and cancellation callbacks.
     *
     * @param title the window title translation key
     * @param subtitle the dialog heading translation key
     * @param content the message translation key
     * @param onConfirm the confirmation callback, or null for no action
     * @param onCancel the cancellation callback, or null for no action
     * @return the shown dialog stage
     */
    public static Stage createDialog(
        String title,
        String subtitle,
        String content,
        Runnable onConfirm,
        Runnable onCancel
    ) {
        return createDialog(title, subtitle, content, dialogBuilder -> {
            dialogBuilder.onConfirm(onConfirm);
            dialogBuilder.onCancel(onCancel);
        });
    }

    /**
     * Shows a dialog after applying optional customization to its translated content.
     *
     * @param title the window title translation key
     * @param subtitle the dialog heading translation key
     * @param content the message translation key
     * @param dialogModifier the customization applied before building the scene, or null for none
     * @return the shown dialog stage
     */
    public static Stage createDialog(
        String title,
        String subtitle,
        String content,
        Consumer<DialogBuilder> dialogModifier
    ) {
        DialogBuilder dialogBuilder = DialogBuilder.create()
            .title(subtitle)
            .content(content);
        if (dialogModifier != null) {
            dialogModifier.accept(dialogBuilder);
        }

        return createDialog(title, dialogBuilder);
    }

    /**
     * Shows a non-resizable utility dialog owned by the primary stage.
     *
     * @param title the window title translation key
     * @param dialogBuilder the dialog configuration, or null to use a default builder
     * @return the shown dialog stage
     */
    public static Stage createDialog(String title, DialogBuilder dialogBuilder) {
        DialogBuilder builder = dialogBuilder == null ? DialogBuilder.create() : dialogBuilder;
        return WindowBuilder.create()
            .title(title, true)
            .owner(Railroad.WINDOW_MANAGER.getPrimaryStage())
            .resizable(false)
            .shouldBlockOwnerWindow(true)
            .stageStyle(StageStyle.UTILITY)
            .scene(builder.buildScene())
            .build();
    }

    /**
     * Sets the stage decoration style.
     *
     * @param stageStyle the stage style
     * @return this builder
     */
    public WindowBuilder stageStyle(StageStyle stageStyle) {
        this.stageStyle = stageStyle;
        return this;
    }

    /**
     * Sets the window title while retaining the current translation setting.
     *
     * @param title the title text or translation key
     * @return this builder
     */
    public WindowBuilder title(String title) {
        this.title = title;
        return this;
    }

    /**
     * Sets the window title and whether it should be translated.
     *
     * @param title the title text or translation key
     * @param translate whether to translate the title
     * @return this builder
     */
    public WindowBuilder title(String title, boolean translate) {
        this.title = title;
        this.translateTitle = translate;
        return this;
    }

    /**
     * Sets whether the window title is translated when the stage is built.
     *
     * @param translate whether to interpret the title as a translation key
     * @return this builder
     */
    public WindowBuilder translateTitle(boolean translate) {
        this.translateTitle = translate;
        return this;
    }

    /**
     * Sets the window scene and immediately applies the current theme to it.
     *
     * @param scene the scene to display
     * @return this builder
     */
    public WindowBuilder scene(Scene scene) {
        this.scene = scene;
        ThemeManager.apply(scene);
        return this;
    }

    /**
     * Sets the image stream used to create the window icon when the stage is built.
     *
     * @param iconStream the icon image stream, or null to omit the icon
     * @return this builder
     */
    public WindowBuilder icon(InputStream iconStream) {
        this.iconStream = iconStream;
        return this;
    }

    /**
     * Sets the window owner. Windows owned by the primary stage are registered as child windows.
     *
     * @param owner the owner window, or null for no owner
     * @return this builder
     */
    public WindowBuilder owner(Window owner) {
        this.owner = owner;
        return this;
    }

    /**
     * Sets the modality controlling which windows are blocked while this stage is shown.
     *
     * @param modality the stage modality
     * @return this builder
     */
    public WindowBuilder modality(Modality modality) {
        this.modality = modality;
        return this;
    }

    /**
     * Sets whether the user can resize the window.
     *
     * @param resizable whether resizing is allowed
     * @return this builder
     */
    public WindowBuilder resizable(boolean resizable) {
        this.resizable = resizable;
        return this;
    }

    /**
     * Sets whether the window starts maximized.
     *
     * @param maximized whether to maximize the stage
     * @return this builder
     */
    public WindowBuilder maximized(boolean maximized) {
        this.maximized = maximized;
        return this;
    }

    /**
     * Stores the owner-blocking preference, which is currently unused when building the stage.
     * Use {@link #modality(Modality)} to control window blocking.
     *
     * @param shouldBlockOwnerWindow the owner-blocking preference to store
     * @return this builder
     */
    public WindowBuilder shouldBlockOwnerWindow(boolean shouldBlockOwnerWindow) {
        this.shouldBlockOwnerWindow = shouldBlockOwnerWindow;
        return this;
    }

    /**
     * Sets a callback invoked after stage configuration and before the window is shown.
     *
     * @param consumer the stage initialization callback, or null for none
     * @return this builder
     */
    public WindowBuilder onInit(Consumer<Stage> consumer) {
        this.onInit = consumer;
        return this;
    }

    /**
     * Sets the minimum window width.
     *
     * @param minWidth the minimum width, or a negative value to leave it unset
     * @return this builder
     */
    public WindowBuilder minWidth(double minWidth) {
        this.minWidth = minWidth;
        return this;
    }

    /**
     * Sets the minimum window height.
     *
     * @param minHeight the minimum height, or a negative value to leave it unset
     * @return this builder
     */
    public WindowBuilder minHeight(double minHeight) {
        this.minHeight = minHeight;
        return this;
    }

    /**
     * Sets the minimum window dimensions.
     *
     * @param minWidth the minimum width, or a negative value to leave it unset
     * @param minHeight the minimum height, or a negative value to leave it unset
     * @return this builder
     */
    public WindowBuilder minSize(double minWidth, double minHeight) {
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        return this;
    }

    /**
     * Sets the initial window width.
     *
     * @param width the initial width, or a negative value to leave it unset
     * @return this builder
     */
    public WindowBuilder width(double width) {
        this.width = width;
        return this;
    }

    /**
     * Sets the initial window height.
     *
     * @param height the initial height, or a negative value to leave it unset
     * @return this builder
     */
    public WindowBuilder height(double height) {
        this.height = height;
        return this;
    }

    /**
     * Sets the initial window dimensions.
     * When both dimensions are negative, the window is sized to its scene if one is supplied.
     *
     * @param width the initial width, or a negative value to leave it unset
     * @param height the initial height, or a negative value to leave it unset
     * @return this builder
     */
    public WindowBuilder size(double width, double height) {
        this.width = width;
        this.height = height;
        return this;
    }

    /**
     * Sets the maximum window width.
     *
     * @param maxWidth the maximum width, or a negative value to leave it unset
     * @return this builder
     */
    public WindowBuilder maxWidth(double maxWidth) {
        this.maxWidth = maxWidth;
        return this;
    }

    /**
     * Sets the maximum window height.
     *
     * @param maxHeight the maximum height, or a negative value to leave it unset
     * @return this builder
     */
    public WindowBuilder maxHeight(double maxHeight) {
        this.maxHeight = maxHeight;
        return this;
    }

    /**
     * Sets the maximum window dimensions.
     *
     * @param maxWidth the maximum width, or a negative value to leave it unset
     * @param maxHeight the maximum height, or a negative value to leave it unset
     * @return this builder
     */
    public WindowBuilder maxSize(double maxWidth, double maxHeight) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        return this;
    }

    /**
     * Sets minimum, initial, and maximum dimensions using the window manager's preferred screen sizing.
     *
     * @return this builder
     * @see WindowManager#calculatePreferredWindowSize()
     */
    public WindowBuilder applyPreferredSize() {
        Matrix3x2d matrix = Railroad.WINDOW_MANAGER.calculatePreferredWindowSize();
        this.minWidth = matrix.m00();
        this.minHeight = matrix.m01();
        this.width = matrix.m10();
        this.height = matrix.m11();
        this.maxWidth = matrix.m20();
        this.maxHeight = matrix.m21();
        return this;
    }

    /**
     * Builds and shows the configured stage, invoking the initialization callback before showing it.
     * Registers windows owned by the primary stage as child windows and releases scene resources
     * when the window is hidden.
     *
     * @return the configured, shown stage
     */
    public Stage build() {
        var stage = new Stage(stageStyle);
        stage.setTitle(translateTitle ? L18n.localize(title) : title);
        stage.setResizable(resizable);
        stage.setMaximized(maximized);

        if (scene != null) {
            stage.setScene(scene);
            stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, _ -> {
                try {
                    Services.UI_MANAGER.releaseScene(scene);
                } finally {
                    ThemeManager.release(scene);
                }
            });
        }

        if (iconStream != null) {
            stage.getIcons().add(new Image(iconStream));
        }

        if (owner != null) {
            stage.initOwner(owner);
        }

        stage.initModality(modality);

        if (minWidth >= 0) {
            stage.setMinWidth(minWidth);
        }
        if (minHeight >= 0) {
            stage.setMinHeight(minHeight);
        }
        if (width >= 0) {
            stage.setWidth(width);
        }
        if (height >= 0) {
            stage.setHeight(height);
        }
        if (maxWidth >= 0) {
            stage.setMaxWidth(maxWidth);
        }
        if (maxHeight >= 0) {
            stage.setMaxHeight(maxHeight);
        }

        // If caller did not specify an explicit size, use the scene's preferred size.
        if (scene != null && width < 0 && height < 0) {
            stage.sizeToScene();
        }

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

        return stage;
    }
}
