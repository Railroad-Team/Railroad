package dev.railroadide.railroad.ide.sst.provider.api;

import dev.railroadide.railroad.ide.sst.document.api.Location;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A problem a rename would cause.
 *
 * @param severity whether the problem prevents the rename or only needs the user's confirmation
 * @param message user-facing explanation
 * @param location affected position in the captured workspace, or {@code null} when the problem has none
 */
public record RenameConflict(Severity severity, String message, Location.@Nullable TextLocation location) {
    /**
     * Validates a conflict.
     *
     * @param severity whether the problem prevents the rename
     * @param message user-facing explanation
     * @param location affected position, or {@code null}
     */
    public RenameConflict {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        if (message.isBlank())
            throw new IllegalArgumentException("Conflicts need a message");
    }

    /** How a conflict affects the rename. */
    public enum Severity {
        /** The rename would break or change the meaning of code and is not offered. */
        ERROR,
        /**
         * The rename can be applied, but something it cannot verify, such as an unresolved occurrence or a
         * documentation link, may be left unchanged.
         */
        WARNING
    }
}
