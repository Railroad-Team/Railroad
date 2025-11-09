package dev.railroadide.railroad.ide.runconfig.ui;

import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.localized.LocalizedTooltip;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.project.Project;
import javafx.geometry.Side;
import javafx.scene.control.ListCell;
import javafx.scene.input.MouseButton;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class RunConfigurationListCell extends ListCell<RunConfiguration<?>> {
    private final RRHBox container = new RRHBox(2);
    private final RRButton runButton = new RRButton("", FontAwesomeSolid.PLAY);
    private final RRButton debugButton = new RRButton("", FontAwesomeSolid.BUG);
    private final RRButton moreActionsButton = new RRButton("", FontAwesomeSolid.ELLIPSIS_V);

    private final Project project;
    private final Runnable editConfigurationsAction;

    public RunConfigurationListCell(Project project, Runnable editConfigurationsAction) {
        this.project = project;
        this.editConfigurationsAction = editConfigurationsAction;

        runButton.setTooltip(new LocalizedTooltip("railroad.runconfig.run.tooltip"));
        runButton.setFocusTraversable(false);
        debugButton.setTooltip(new LocalizedTooltip("railroad.runconfig.debug.tooltip"));
        debugButton.setFocusTraversable(false);
        moreActionsButton.setTooltip(new LocalizedTooltip("railroad.runconfig.moreactions.tooltip"));
        moreActionsButton.setFocusTraversable(false);
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
            setText(L18n.localize("railroad.ide.toolbar.edit_run_configurations"));
            setGraphic(new FontIcon(FontAwesomeSolid.COG));
            setOnMouseClicked(mouseEvent -> {
                if (mouseEvent.getButton() == MouseButton.PRIMARY) {
                    mouseEvent.consume();
                    if (editConfigurationsAction != null) {
                        editConfigurationsAction.run();
                    }
                }
            });
            return;
        }

        setOnMouseClicked(null);
        setText(item.data().getName());
        runButton.setOnAction(event -> item.run(project));
        debugButton.setOnAction(event -> item.debug(project));
        moreActionsButton.setOnAction(event -> {
            var menu = item.createContextMenu(project);
            RunConfigurationContextMenuManager.show(moreActionsButton, menu, Side.BOTTOM);
        });
        container.getChildren().setAll(runButton, debugButton, moreActionsButton);
        setGraphic(container);
    }
}
