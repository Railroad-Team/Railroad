package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.IDEViewMode;
import dev.railroadide.railroad.ide.IDEViewModeController;
import dev.railroadide.railroad.ide.ui.setup.IDEMenuBarFactory;
import dev.railroadide.railroad.ide.ui.setup.RunControlsPane;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.id.UIIds;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.Objects;
import java.util.function.Consumer;

public final class IDETopBarPane extends RRHBox {
    public IDETopBarPane(
        Project project,
        IDEViewModeController viewModeController,
        Consumer<IDEViewMode> viewModeRequester
    ) {
        Objects.requireNonNull(project, "Project cannot be null");
        Objects.requireNonNull(viewModeController, "View mode controller cannot be null");
        Objects.requireNonNull(viewModeRequester, "View mode requester cannot be null");

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(
            IDEMenuBarFactory.create(project, viewModeController, viewModeRequester),
            spacer,
            RunControlsPane.create(project)
        );

        Services.UI_MANAGER.assignWhileAttached(UIIds.IDE.IDE_TOP_BAR, this);
    }
}
