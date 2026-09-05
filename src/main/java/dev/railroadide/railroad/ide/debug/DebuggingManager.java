package dev.railroadide.railroad.ide.debug;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.Getter;

/**
 * Holds observable active and paused debug state for a project.
 */
public final class DebuggingManager {
    private final BooleanProperty active = new SimpleBooleanProperty(this, "active", false);
    private final BooleanProperty paused = new SimpleBooleanProperty(this, "paused", false);

    @Getter
    private final Project project;

    /**
     * Creates debug state for a project with both flags initially false.
     *
     * @param project project whose editor features or debug state are managed
     */
    public DebuggingManager(Project project) {
        this.project = project;
    }

    /**
     * Checks the current debug active flag.
     *
     * @return whether debugging is active
     */
    public boolean isActive() {
        return active.get();
    }

    /**
     * Checks the current debug paused flag.
     *
     * @return whether debugging is paused
     */
    public boolean isPaused() {
        return paused.get();
    }

    /**
     * Exposes the mutable debug active flag.
     *
     * @return observable active state
     */
    public BooleanProperty activeProperty() {
        return active;
    }

    /**
     * Exposes the mutable debug paused flag.
     *
     * @return observable paused state
     */
    public BooleanProperty pausedProperty() {
        return paused;
    }
}
