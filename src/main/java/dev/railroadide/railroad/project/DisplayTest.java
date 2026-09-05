package dev.railroadide.railroad.project;

/**
 * Enum representing different display test options. Display tests control how a mod handles version matching and
 * display on the server connection screen when a client joins a server.
 * <p>
 * The default value for this is usually {@link #MATCH_VERSION}, which requires the client and server to have
 * matching mod versions. Other options allow for more lenient version checking or completely ignore version checks.
 */
public enum DisplayTest {
    /**
     * (Default) Requires the client and server to have matching mod versions. If they do not match,
     * the connection may be rejected or warned.
     */
    MATCH_VERSION,
    /**
     * Allows the client to connect even if the server's version of the mod is different or if the server doesn't
     * strictly enforce identical version numbering for this mod.
     */
    IGNORE_SERVER_VERSION,
    /**
     * Completely ignores version checking for this mod between client and server. This is frequently used for
     * server-only utility or performance mods where clients do not need a matching copy.
     */
    IGNORE_ALL_VERSION,
    /**
     * Disables display and checking functionality for the server connection screen entirely.
     */
    NONE
}
