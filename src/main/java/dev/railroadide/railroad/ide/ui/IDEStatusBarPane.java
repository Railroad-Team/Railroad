package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.id.UIIds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * Provides the registered status-bar container for the active IDE workspace.
 */
public class IDEStatusBarPane extends RRHBox {
    /**
     * Places workspace tools on the left and the task toggle and caret position on the right.
     *
     * @param toolButtons workspace tool buttons at the left edge
     * @param activityPane task progress toggle
     */
    public IDEStatusBarPane(Node toolButtons, IDEActivityPane activityPane) {
        Services.UI_MANAGER.assignWhileAttached(UIIds.IDE.IDE_STATUS_BAR, this);
        var indicators = new RRHBox(8, activityPane, new IDECaretPositionPane());
        indicators.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(indicators, Priority.ALWAYS);
        HBox.setMargin(indicators, new Insets(8));
        getChildren().addAll(toolButtons, indicators);
    }
}
