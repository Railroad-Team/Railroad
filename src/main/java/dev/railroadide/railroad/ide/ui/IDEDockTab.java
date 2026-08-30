package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.localized.LocalizedTab;

import java.util.Objects;

/** A localized tab backed by a stable {@link IDEDockItem} descriptor. */
public final class IDEDockTab extends LocalizedTab {
    private final IDEDockItem dockItem;

    public IDEDockTab(IDEDockItem dockItem, Project project) {
        super(Objects.requireNonNull(dockItem, "Dock item cannot be null").localizationKey());
        this.dockItem = dockItem;
        setId(dockItem.id());
        setClosable(false);

        if (dockItem.initializationPolicy() == IDEDockItem.InitializationPolicy.ON_FIRST_SELECTION) {
            setOnSelectionChanged(_ -> {
                if (isSelected() && getContent() == null) {
                    setContent(dockItem.createContent(project));
                    setOnSelectionChanged(null);
                }
            });
        } else {
            setContent(dockItem.createContent(project));
        }
    }

    public IDEDockItem getDockItem() {
        return dockItem;
    }
}
