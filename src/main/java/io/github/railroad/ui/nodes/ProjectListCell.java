package io.github.railroad.ui.nodes;

import io.github.palexdev.mfxcore.builders.InsetsBuilder;
import io.github.railroad.Railroad;
import io.github.railroad.project.Project;
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
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * A modern list cell component for displaying project items in project lists.
 * Features a card-based design with project icon, name, path, last opened date, and context menu actions.
 * Supports open and remove project functionality through a context menu.
 */
public class ProjectListCell extends ListCell<Project> {
    private final RRCard card = new RRCard(14, new Insets(12, 32, 12, 32));
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
        setMinHeight(80);
        setMaxHeight(80);

        card.getStyleClass().add("project-list-card");
        card.setPadding(InsetsBuilder.left(5));
        card.setOnMouseEntered($ -> card.getStyleClass().add("rr-card-hover"));
        card.setOnMouseExited($ -> card.getStyleClass().remove("rr-card-hover"));
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
        ellipsisButton.setPrefWidth(32);
        ellipsisButton.setPrefHeight(32);
        ellipsisButton.setMinWidth(32);
        ellipsisButton.setMinHeight(32);
        ellipsisButton.setMaxWidth(32);
        ellipsisButton.setMaxHeight(32);
        ellipsisButton.getStyleClass().add("project-list-ellipsis-button");
        ellipsisButton.setOnMouseEntered($ -> ellipsisButton.getStyleClass().add("project-list-ellipsis-button-hover"));
        ellipsisButton.setOnMouseExited($ -> ellipsisButton.getStyleClass().remove("project-list-ellipsis-button-hover"));

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
        } else {
            icon.setImage(project.getIcon());
            nameLabel.setText(project.getAlias());
            pathLabel.setText(project.getPathString());
            lastOpenedLabel.setText(Project.getLastOpenedFriendly(project.getLastOpened()));
            setGraphic(card);
        }
    }
} 