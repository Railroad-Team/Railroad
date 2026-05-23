package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.IDEViewMode;
import dev.railroadide.railroad.ide.ui.setup.IDEMenuBarFactory;
import dev.railroadide.railroad.ide.ui.setup.IDEViewModeToggle;
import dev.railroadide.railroad.ide.ui.setup.RunControlsPane;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.ui.localized.LocalizedTooltip;
import javafx.beans.property.ObjectProperty;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.Objects;

public final class IDETopBarPane extends RRHBox {
    public IDETopBarPane(Project project, ObjectProperty<IDEViewMode> viewModeProperty) {
        Objects.requireNonNull(project, "Project cannot be null");
        Objects.requireNonNull(viewModeProperty, "View mode property cannot be null");

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        var viewModeToggle = new IDEViewModeToggle(viewModeProperty);
        Tooltip.install(viewModeToggle, new LocalizedTooltip("railroad.ide.view_mode.tooltip"));

        getChildren().addAll(
            IDEMenuBarFactory.create(project, viewModeProperty),
            viewModeToggle,
            spacer,
            RunControlsPane.create(project)
        );

        Services.UI_MANAGER.assignWhileAttached(UIIds.IDE.IDE_TOP_BAR, this);
    }
}
