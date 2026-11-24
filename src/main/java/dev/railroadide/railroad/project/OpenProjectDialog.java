package dev.railroadide.railroad.project;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.RailroadProcessLauncher;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.window.WindowBuilder;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.util.Objects;

/**
 * Asks whether a project opens in the current window, replacing the open project, or in a new Railroad window.
 * <p>
 * Enter opens the focused choice, which is "This window" initially; the arrow keys move between the choices and
 * Escape cancels. If a new window cannot be started, the dialog stays open and shows why.
 */
public final class OpenProjectDialog {
    private static final double ICON_SIZE = 40;
    private static final String ACTION = "railroad:open-project-action";

    private final Project project;
    private final @Nullable Project current;
    private final Label error = new Label();
    private @Nullable Stage stage;

    private OpenProjectDialog(Project project, @Nullable Project current) {
        this.project = Objects.requireNonNull(project, "project");
        this.current = current;
    }

    /**
     * Shows the dialog for a project, owned by the primary window.
     *
     * @param project the project to open
     */
    public static void show(Project project) {
        new OpenProjectDialog(project, Railroad.PROJECT_MANAGER.getOpenProject())
            .show(Railroad.WINDOW_MANAGER.getPrimaryStage());
    }

    private void show(Window owner) {
        var thisWindow = choice(FontAwesomeSolid.WINDOW_MAXIMIZE, "railroad.open_project.this_window.title",
            current == null
                ? L18n.localize("railroad.open_project.this_window.description")
                : L18n.localize("railroad.open_project.this_window.replaces", current.getAlias()),
            this::openHere);
        var newWindow = choice(FontAwesomeSolid.CLONE, "railroad.open_project.new_window.title",
            current == null
                ? L18n.localize("railroad.open_project.new_window.description")
                : L18n.localize("railroad.open_project.new_window.keeps", current.getAlias()),
            this::openInNewWindow);

        error.getStyleClass().add("open-project-error");
        error.setWrapText(true);
        error.setVisible(false);
        error.setManaged(false);

        var cancel = new RRButton("railroad.generic.cancel");
        cancel.setVariant(ButtonVariant.SECONDARY);
        cancel.setOnAction(_ -> close());
        var footer = new HBox(cancel);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.getStyleClass().add("open-project-footer");

        var prompt = new LocalizedLabel("railroad.open_project.prompt");
        prompt.getStyleClass().add("open-project-prompt");
        var choices = new VBox(8, thisWindow, newWindow);

        var content = new VBox(header(), prompt, choices, error, footer);
        content.getStyleClass().add("open-project-dialog");

        var scene = new Scene(content);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
                close();
            } else if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                Node focused = scene.getFocusOwner();
                if (focused != null && focused.getProperties().get(ACTION) instanceof Runnable action) {
                    event.consume();
                    action.run();
                } else if (event.getCode() == KeyCode.ENTER && focused instanceof ButtonBase button) {
                    event.consume();
                    button.fire();
                }
            }
        });

        stage = WindowBuilder.create()
            .title("railroad.open_project.title", true)
            .owner(owner)
            .modality(Modality.WINDOW_MODAL)
            .resizable(false)
            .scene(scene)
            .build();
        thisWindow.requestFocus();
    }

    private HBox header() {
        var name = new Label(project.getAlias());
        name.getStyleClass().add("open-project-name");
        var path = new Label(project.getPathString());
        path.getStyleClass().add("open-project-path");
        path.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        var text = new VBox(2, name, path);
        text.setAlignment(Pos.CENTER_LEFT);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        var header = new HBox(12, projectIcon(), text);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("open-project-header");
        return header;
    }

    private StackPane projectIcon() {
        var frame = new StackPane();
        frame.getStyleClass().add("open-project-icon");
        frame.setMinSize(ICON_SIZE, ICON_SIZE);
        frame.setMaxSize(ICON_SIZE, ICON_SIZE);
        Image image = project.getIcon();
        if (image != null) {
            var view = new ImageView(image);
            view.setFitWidth(ICON_SIZE);
            view.setFitHeight(ICON_SIZE);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            var clip = new Rectangle(ICON_SIZE, ICON_SIZE);
            clip.setArcWidth(10);
            clip.setArcHeight(10);
            view.setClip(clip);
            frame.getChildren().add(view);
        } else {
            frame.getChildren().add(new FontIcon(FontAwesomeSolid.FOLDER_OPEN));
        }
        return frame;
    }

    /**
     * Builds a focusable choice row. A row rather than a button graphic keeps the description's wrapping in the
     * layout's width-dependent sizing, so the window fits its content.
     */
    private static HBox choice(Ikon icon, String titleKey, String description, Runnable action) {
        var glyph = new FontIcon(icon);
        glyph.getStyleClass().add("open-project-choice-icon");
        var tile = new StackPane(glyph);
        tile.getStyleClass().add("open-project-choice-tile");

        var title = new LocalizedLabel(titleKey);
        title.getStyleClass().add("open-project-choice-title");
        var detail = new Label(description);
        detail.getStyleClass().add("open-project-choice-description");
        detail.setWrapText(true);
        var text = new VBox(2, title, detail);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        var row = new HBox(12, tile, text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("open-project-choice");
        row.setFocusTraversable(true);
        row.setAccessibleRole(AccessibleRole.BUTTON);
        row.accessibleTextProperty().bind(title.textProperty().concat(". ").concat(description));
        row.getProperties().put(ACTION, action);
        row.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                action.run();
            }
        });
        return row;
    }

    private void openHere() {
        close();
        project.open(Railroad.WINDOW_MANAGER.getPrimaryStage());
    }

    private void openInNewWindow() {
        try {
            RailroadProcessLauncher.openProject(project.getPath());
            close();
        } catch (IOException exception) {
            Railroad.LOGGER.error("An error occurred trying to start a new Railroad process", exception);
            error.setText(L18n.localize("railroad.open_project.new_window.failed", exception.getMessage()));
            error.setVisible(true);
            error.setManaged(true);
            if (stage != null) {
                stage.sizeToScene();
            }
        }
    }

    private void close() {
        if (stage != null) {
            stage.close();
        }
    }
}
