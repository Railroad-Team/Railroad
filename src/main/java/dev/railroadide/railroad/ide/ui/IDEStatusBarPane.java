package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.id.UIIds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Provides the registered status-bar container for the active IDE workspace.
 */
public class IDEStatusBarPane extends RRHBox {
    /**
     * Creates a status bar showing running background work on the left and the caret position on the right.
     * Registers the pane with the UI manager while attached to a scene.
     */
    public IDEStatusBarPane() {
        Services.UI_MANAGER.assignWhileAttached(UIIds.IDE.IDE_STATUS_BAR, this);
        var activityPane = new IDEActivityPane();
        var spacer = new Region();
        var ideCaretPositionPane = new IDECaretPositionPane();

        setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(this, Priority.ALWAYS);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        setMargin(this, new Insets(8));
        this.getChildren().addAll(activityPane, spacer, ideCaretPositionPane);
    }
}
