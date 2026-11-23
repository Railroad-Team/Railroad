package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.id.UIIds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * Provides the registered status-bar container for the active IDE workspace.
 */
public class IDEStatusBarPane extends RRHBox {
    /**
     * Creates a status bar with running background work beside the caret position at the right edge.
     * Registers the pane with the UI manager while attached to a scene.
     */
    public IDEStatusBarPane() {
        super(8);
        Services.UI_MANAGER.assignWhileAttached(UIIds.IDE.IDE_STATUS_BAR, this);
        var activityPane = new IDEActivityPane();
        var ideCaretPositionPane = new IDECaretPositionPane();

        setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(this, Priority.ALWAYS);
        setMargin(this, new Insets(8));
        this.getChildren().addAll(activityPane, ideCaretPositionPane);
    }
}
