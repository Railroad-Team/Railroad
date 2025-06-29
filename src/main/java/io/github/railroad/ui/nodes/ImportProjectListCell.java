package io.github.railroad.ui.nodes;

import io.github.railroad.Railroad;
import io.github.railroad.vcs.Repository;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.scene.paint.Color;
import javafx.scene.effect.DropShadow;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;

/**
 * A modern list cell component for displaying repository items in import project lists.
 * Features a card-based design with repository icon, name, URL, and context menu actions.
 * Supports clone functionality through a directory chooser dialog.
 */
public class ImportProjectListCell extends ListCell<Repository> {
    private final RRCard card = new RRCard(12, new Insets(12, 18, 12, 18));
    private final HBox content = new HBox(14);
    private final ImageView icon = new ImageView();
    private final VBox infoBox = new VBox(4);
    private final RRButton ellipsisButton = new RRButton("...");

    /**
     * Constructs a new ImportProjectListCell with modern styling and context menu functionality.
     * Sets up the card layout with repository icon, information display, and ellipsis button
     * for accessing clone and other repository actions.
     */
    public ImportProjectListCell() {
        card.getStyleClass().add("import-project-card");
        icon.setFitWidth(32);
        icon.setFitHeight(32);
        icon.setPreserveRatio(true);
        icon.setSmooth(true);
        icon.setEffect(new DropShadow(4, Color.rgb(0, 0, 0, 0.10)));

        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(0));

        infoBox.setAlignment(Pos.CENTER_LEFT);
        infoBox.setSpacing(8);
        VBox.setVgrow(infoBox, Priority.ALWAYS);

        var ellipsisIcon = new FontIcon(FontAwesomeSolid.ELLIPSIS_V);
        ellipsisIcon.setIconSize(16);
        ellipsisButton.setGraphic(ellipsisIcon);
        ellipsisButton.setButtonSize(RRButton.ButtonSize.SMALL);
        ellipsisButton.getStyleClass().add("import-project-ellipsis-button");

        content.getChildren().addAll(icon, infoBox, ellipsisButton);
        card.getChildren().add(content);

        setPadding(new Insets(4, 0, 4, 0));

        var dropdown = new ContextMenu();
        var cloneItem = new MenuItem("Clone");
        cloneItem.setOnAction($ -> {
            var directoryChooser = new DirectoryChooser();
            File selectedDirectory = directoryChooser.showDialog(Railroad.getWindow());
            Repository repository = getItem();
            if (repository != null && selectedDirectory != null) {
                if (repository.cloneRepo(selectedDirectory.toPath())) {
                    // TODO: Handle successful clone
                }
            }
        });

        dropdown.getItems().add(cloneItem);
        ellipsisButton.setOnAction($ ->
                dropdown.show(ellipsisButton, Side.BOTTOM, 0, 0));
    }

    @Override
    protected void updateItem(Repository repository, boolean empty) {
        super.updateItem(repository, empty);
        if (empty || repository == null) {
            setText(null);
            setGraphic(null);
        } else {
            icon.setImage(repository.getIcon().orElse(null));
            infoBox.getChildren().clear();

            var nameLabel = new Label(repository.getRepositoryName());
            nameLabel.getStyleClass().add("import-project-name");

            var urlLabel = new Label(repository.getRepositoryURL());
            urlLabel.getStyleClass().add("import-project-url");

            infoBox.getChildren().addAll(nameLabel, urlLabel);
            setGraphic(card);
        }
    }
} 