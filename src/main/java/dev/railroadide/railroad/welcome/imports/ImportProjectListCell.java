package dev.railroadide.railroad.welcome.imports;

import dev.railroadide.railroad.ui.RRCard;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.vcs.Repository;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/** Repository card cell displaying its name, URL, and optional icon in the import browser. */
public class ImportProjectListCell extends ListCell<Repository> {
    private final RRCard card = new RRCard(12);
    private final HBox content = new RRHBox();
    private final ImageView icon = new ImageView();
    private final VBox infoBox = new RRVBox();

    /** Creates the reusable repository card with a 32-pixel icon area and text container. */
    public ImportProjectListCell() {
        card.getStyleClass().add("import-project-card");
        icon.setFitWidth(32);
        icon.setFitHeight(32);
        icon.setPreserveRatio(true);
        icon.setSmooth(true);
        icon.setEffect(new DropShadow(4, Color.rgb(0, 0, 0, 0.10)));

        content.setAlignment(Pos.CENTER_LEFT);
        content.getStyleClass().add("import-project-content-row");

        infoBox.setAlignment(Pos.CENTER_LEFT);
        infoBox.getStyleClass().add("import-project-info-box");
        VBox.setVgrow(infoBox, Priority.ALWAYS);

        content.getChildren().addAll(icon, infoBox);
        content.setFillHeight(true);
        content.setAlignment(Pos.CENTER_LEFT);
        content.getStyleClass().add("import-project-content");
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        card.getChildren().add(content);

        getStyleClass().add("import-project-list-cell");
    }

    /**
     * Refreshes repository details and selection styling, removing the icon when none is available.
     *
     * @param repository repository to display, or null
     * @param empty whether the cell has no item
     */
    @Override
    protected void updateItem(Repository repository, boolean empty) {
        super.updateItem(repository, empty);
        if (empty || repository == null) {
            setText(null);
            setGraphic(null);
        } else {
            icon.setImage(repository.getIcon().orElse(null));
            if (icon.getImage() == null) {
                content.getChildren().remove(icon);
            } else if (!content.getChildren().contains(icon)) {
                content.getChildren().addFirst(icon);
            }

            infoBox.getChildren().clear();

            var nameLabel = new Label(repository.getRepositoryName());
            nameLabel.getStyleClass().add("import-project-name");

            var urlLabel = new Label(repository.getRepositoryURL());
            urlLabel.getStyleClass().add("import-project-url");

            infoBox.getChildren().addAll(nameLabel, urlLabel);
            setGraphic(card);

            if (isSelected()) {
                card.getStyleClass().add("import-project-card-selected");
            } else {
                card.getStyleClass().remove("import-project-card-selected");
            }
        }
    }
}
