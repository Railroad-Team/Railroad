package dev.railroadide.railroad.ide.runconfig.ui;

import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.localized.LocalizedTooltip;
import dev.railroadide.core.ui.styling.ButtonSize;
import dev.railroadide.core.ui.styling.ButtonVariant;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.project.Project;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class RunConfigurationListCell extends ListCell<RunConfiguration<?>> {
    private final RRHBox container = new RRHBox(6);
    private final Label nameLabel = new Label();
    private final RRButton runButton = new RRButton("", FontAwesomeSolid.PLAY);
    private final RRButton debugButton = new RRButton("", FontAwesomeSolid.BUG);
    private final RRButton moreActionsButton = new RRButton("", FontAwesomeSolid.ELLIPSIS_V);

    private final Project project;

    public RunConfigurationListCell(Project project) {
        this.project = project;

        container.setAlignment(Pos.CENTER_LEFT);
        container.getStyleClass().add("run-config-combobox-item");
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        container.getChildren().setAll(nameLabel, spacer, runButton, debugButton, moreActionsButton);

        runButton.setTooltip(new LocalizedTooltip("railroad.runconfig.run.tooltip"));
        runButton.setSquare(true);
        runButton.setButtonSize(ButtonSize.SMALL);
        runButton.setVariant(ButtonVariant.GHOST);
        runButton.getStyleClass().add("run-button");
        runButton.setFocusTraversable(false);
        runButton.setOnAction(event -> {
            RunConfiguration<?> config = getItem();
            if (config != null) {
                config.run(project);
            }
        });

        debugButton.setTooltip(new LocalizedTooltip("railroad.runconfig.debug.tooltip"));
        debugButton.setSquare(true);
        debugButton.setButtonSize(ButtonSize.SMALL);
        debugButton.setVariant(ButtonVariant.GHOST);
        debugButton.getStyleClass().add("debug-button");
        debugButton.setFocusTraversable(false);
        debugButton.setOnAction(event -> {
            RunConfiguration<?> config = getItem();
            if (config != null) {
                config.debug(project);
            }
        });

        moreActionsButton.setTooltip(new LocalizedTooltip("railroad.runconfig.moreactions.tooltip"));
        moreActionsButton.setSquare(true);
        moreActionsButton.setButtonSize(ButtonSize.SMALL);
        moreActionsButton.setVariant(ButtonVariant.GHOST);
        moreActionsButton.getStyleClass().add("more-actions-button");
        moreActionsButton.setFocusTraversable(false);
        moreActionsButton.setOnAction(event -> {
            RunConfiguration<?> config = getItem();
            if (config != null) {
                var menu = config.createContextMenu(project);
                RunConfigurationContextMenuManager.show(moreActionsButton, menu, Side.BOTTOM);
            }
        });
    }

    @Override
    protected void updateItem(RunConfiguration item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            setText(null);
            setGraphic(null);
            setOnMouseClicked(null);
            return;
        }

        if (item == null) {
            nameLabel.setText(null);
            setText(L18n.localize("railroad.ide.toolbar.edit_run_configurations"));
            setGraphic(new FontIcon(FontAwesomeSolid.COG));
            return;
        }

        setOnMouseClicked(null);
        setText(null);
        nameLabel.setText(item.data().getName());
        debugButton.setDisable(!item.isDebuggingSupported(project));
        setGraphic(container);
    }
}
