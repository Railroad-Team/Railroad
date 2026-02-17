package dev.railroadide.railroad.ide.ui.git.commit.changes;

import dev.railroadide.core.ui.RRCheckBoxTreeCell;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRStackPane;
import javafx.scene.Node;
import javafx.scene.text.Text;

import java.util.Objects;

public class CommitChangeTreeCell extends RRCheckBoxTreeCell<ChangeItem> {
    private final RRHBox container = new RRHBox(8);
    private final RRStackPane iconContainer = new RRStackPane();
    private final RRHBox textContainer = new RRHBox(2);
    private final Text titleText = new Text();
    private final Text subtitleText = new Text();

    public CommitChangeTreeCell() {
        container.getStyleClass().add("git-commit-change-tree-cell");
        iconContainer.getStyleClass().add("git-commit-change-icon-container");

        titleText.getStyleClass().add("git-commit-change-title-text");
        subtitleText.getStyleClass().add("git-commit-change-subtitle-text");
        textContainer.getChildren().addAll(titleText, subtitleText);

        container.getChildren().addAll(iconContainer, textContainer);
    }

    @Override
    protected void updateItem(ChangeItem item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setGraphic(null);
            setText(null);
            iconContainer.getChildren().clear();
            setOnMouseClicked(null);
        } else {
            if (!iconContainer.getChildren().isEmpty()) {
                Node oldIcon = iconContainer.getChildren().getFirst();
                if (!Objects.equals(oldIcon, item.getIcon())) {
                    iconContainer.getChildren().clear();
                    if (item.getIcon() != null) {
                        iconContainer.getChildren().add(item.getIcon());
                    }
                }
            } else {
                if (item.getIcon() != null) {
                    iconContainer.getChildren().add(item.getIcon());
                    if (!container.getChildren().contains(iconContainer)) {
                        container.getChildren().addFirst(iconContainer);
                    }
                } else {
                    container.getChildren().remove(iconContainer);
                }
            }

            titleText.setText(item.getTitle());
            String subtitle = item.getSubtitle();
            if (subtitle != null && !subtitle.isEmpty()) {
                subtitleText.setText(subtitle);
                subtitleText.setVisible(true);
                if (!textContainer.getChildren().contains(subtitleText)) {
                    textContainer.getChildren().add(subtitleText);
                }
            } else {
                subtitleText.setText("");
                subtitleText.setVisible(false);
                textContainer.getChildren().remove(subtitleText);
            }

            textContainer.getStyleClass().clear();
            textContainer.getStyleClass().add("git-commit-change-text-container");
            textContainer.getStyleClass().addAll(item.getStyleClass().split(" "));

            setCustomContent(container);

            setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !event.isConsumed()) {
                    if (item.getDoubleClickHandler() != null) {
                        item.getDoubleClickHandler().accept(event);
                    }
                }
            });
        }
    }
}
