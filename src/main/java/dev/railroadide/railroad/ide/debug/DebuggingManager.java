package dev.railroadide.railroad.ide.debug;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.Getter;

public final class DebuggingManager {
    private final BooleanProperty active = new SimpleBooleanProperty(this, "active", false);
    private final BooleanProperty paused = new SimpleBooleanProperty(this, "paused", false);

    @Getter
    private final Project project;

    public DebuggingManager(Project project) {
        this.project = project;
    }

    public boolean isActive() {
        return active.get();
    }

    public boolean isPaused() {
        return paused.get();
    }

    public BooleanProperty activeProperty() {
        return active;
    }

    public BooleanProperty pausedProperty() {
        return paused;
    }
}
