package dev.railroadide.railroad.ui.nodes;

import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRCard;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.utility.StringUtils;
import io.github.palexdev.mfxcore.builders.InsetsBuilder;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * A modern list cell component for displaying project items in project lists.
 * Features a card-based design with project icon, name, path, last opened date, and context menu actions.
 * Supports open and remove project functionality through a context menu.
 */
public class ProjectListCell extends ListCell<Project> {
    private final RRCard card = new RRCard(14, new Insets(8, 32, 8, 32));
    private final HBox root = new HBox(16);
    private final ImageView icon = new ImageView();
    private final VBox infoBox = new VBox(7);
    private final Label nameLabel = new Label();
    private final Label pathLabel = new Label();
    private final Label lastOpenedLabel = new Label();
    private final RRButton ellipsisButton = new RRButton();

    /**
     * Constructs a new ProjectListCell with modern styling and context menu functionality.
     * Sets up the card layout with project icon, information display, and ellipsis button
     * for accessing open and remove project actions.
     */
    public ProjectListCell() {
        super();
        getStyleClass().add("project-list-cell");

        setPrefHeight(80);

        card.getStyleClass().add("project-list-card");
        card.setPadding(InsetsBuilder.of(10, 5, 10, 5));
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(0));

        icon.setFitWidth(40);
        icon.setFitHeight(40);
        icon.setPreserveRatio(true);
        icon.setSmooth(true);
        icon.setEffect(new DropShadow(8, Color.rgb(0, 0, 0, 0.10)));

        infoBox.setAlignment(Pos.CENTER_LEFT);
        infoBox.setSpacing(7);
        VBox.setVgrow(infoBox, Priority.ALWAYS);
        nameLabel.getStyleClass().add("project-list-name");
        pathLabel.getStyleClass().add("project-list-path");
        lastOpenedLabel.getStyleClass().add("project-list-last-opened");
        infoBox.getChildren().addAll(nameLabel, pathLabel, lastOpenedLabel);

        var ellipsisIcon = new FontIcon(FontAwesomeSolid.ELLIPSIS_V);
        ellipsisIcon.setIconSize(16);
        ellipsisButton.setGraphic(ellipsisIcon);
        ellipsisButton.setButtonSize(RRButton.ButtonSize.SMALL);
        ellipsisButton.setVariant(RRButton.ButtonVariant.GHOST);
        ellipsisButton.setPrefWidth(32);
        ellipsisButton.setPrefHeight(32);
        ellipsisButton.getStyleClass().add("project-list-ellipsis-button");

        root.getChildren().addAll(icon, infoBox, ellipsisButton);
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        HBox.setHgrow(ellipsisButton, Priority.NEVER);
        card.getChildren().setAll(root);

        var dropdown = new ContextMenu();
        var openItem = new MenuItem("Open");
        openItem.setOnAction($ -> {
            Project project = getItem();
            if (project != null) {
                project.open();
            }
        });

        var removeItem = new MenuItem("Remove");
        removeItem.setOnAction($ -> {
            Project project = getItem();
            if (project != null) {
                Railroad.PROJECT_MANAGER.removeProject(project);
            }
        });

        dropdown.getItems().addAll(openItem, removeItem);
        ellipsisButton.setOnAction($ -> dropdown.show(ellipsisButton, Side.BOTTOM, 0, 0));
    }

    @Override
    protected void updateItem(Project project, boolean empty) {
        super.updateItem(project, empty);
        if (empty || project == null) {
            setText(null);
            setGraphic(null);
            setPadding(Insets.EMPTY);
        } else {
            icon.setImage(project.getIcon());
            nameLabel.setText(project.getAlias());
            pathLabel.setText(project.getPathString());
            lastOpenedLabel.setText(StringUtils.formatElapsed(project.getLastOpened()));
            setGraphic(card);
        }
    }
}