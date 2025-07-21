package dev.railroadide.railroad.welcome.imports;

import dev.railroadide.core.ui.RRCard;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.vcs.Repository;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;


public class ImportProjectListCell extends ListCell<Repository> {
    private final RRCard card = new RRCard(12, new Insets(12, 18, 12, 18));
    private final HBox content = new RRHBox(14);
    private final ImageView icon = new ImageView();
    private final VBox infoBox = new RRVBox(4);

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

        content.getChildren().addAll(icon, infoBox);
        content.setFillHeight(true);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(8));
        content.getStyleClass().add("import-project-content");
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        card.getChildren().add(content);

        setPadding(new Insets(4, 0, 4, 0));
    }

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