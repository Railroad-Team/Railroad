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
     * Returns all registered workspace mode IDs.
     *
     * @return the IDs of all workspace modes known to this application
     */
    Set<String> getModeIds();

    /**
     * Returns the modes that can currently be activated.
     *
     * @return the IDs currently available in the active workspace, or an empty set if no workspace is active
     */
    Set<String> getAvailableModeIds();

    /**
     * Returns the mode selected in the active workspace.
     *
     * @return the active mode ID, or {@code null} if no workspace is active
     */
    String getActiveModeId();

    /**
     * Checks whether a known mode can currently be activated.
     *
     * @param modeId registry ID of the workspace mode to check
     * @return {@code true} if the mode is available in the active workspace
     */
    boolean isModeAvailable(String modeId);

    /**
     * Requests a validated workspace-mode activation. The result is completed on the application thread after the
     * active workspace and current availability have been checked.
     *
     * @param modeId registry ID of the workspace mode to activate
     * @return a future containing the activation outcome
     */
    CompletableFuture<ActivationResult> activateMode(String modeId);

    /** Describes the outcome of a workspace mode activation request. */
    enum ActivationResult {
        /** The requested mode is active, including when it was already selected. */
        ACTIVATED,
        /** No mode is registered with the requested ID. */
        UNKNOWN_MODE,
        /** The mode is registered but cannot currently be activated. */
        UNAVAILABLE,
        /** No workspace is active to receive the request. */
        NO_ACTIVE_WORKSPACE
    }
}
