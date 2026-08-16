package dev.railroadide.railroad.plugin.spi.services;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Provides plugin-safe access to the active IDE workspace mode.
 * <p>
 * Modes are identified by stable registry IDs so plugins do not depend on application implementation types or JavaFX
 * state.
 */
public interface WorkspaceService {
    /**
     * @return the IDs of all workspace modes known to this application
     */
    Set<String> getModeIds();

    /**
     * @return the IDs currently available in the active workspace, or an empty set if no workspace is active
     */
    Set<String> getAvailableModeIds();

    /**
     * @return the active mode ID, or {@code null} if no workspace is active
     */
    String getActiveModeId();

    /**
     * Checks whether a known mode can currently be activated.
     */
    boolean isModeAvailable(String modeId);

    /**
     * Requests a validated workspace-mode activation. The result is completed on the application thread after the
     * active workspace and current availability have been checked.
     */
    CompletableFuture<ActivationResult> activateMode(String modeId);

    enum ActivationResult {
        ACTIVATED,
        UNKNOWN_MODE,
        UNAVAILABLE,
        NO_ACTIVE_WORKSPACE
    }
}
