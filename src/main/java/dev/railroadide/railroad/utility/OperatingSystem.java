package dev.railroadide.railroad.utility;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Enum representing the operating system on which the application is running.
 * It provides methods to detect the current operating system and retrieve it.
 */
public enum OperatingSystem {
    /**
     * Represents the Windows operating system.
     */
    WINDOWS,
    /**
     * Represents the Mac operating system.
     */
    MAC,
    /**
     * Represents the Linux operating system.
     */
    LINUX,
    /**
     * Represents an unknown or unsupported operating system.
     */
    UNKNOWN;

    /**
     * The current operating system detected at runtime.
     * This is a static final field that is initialized when the class is loaded.
     */
    @NotNull
    public static final OperatingSystem CURRENT = detect();

    /**
     * Detects the operating system based on the system property "os.name".
     * It checks for known substrings to determine if the OS is Windows, Mac, Linux, or unknown.
     *
     * @return the detected OperatingSystem enum value
     */
    public static @NotNull OperatingSystem detect() {
        String os = System.getProperty("os.name", "unknown").toLowerCase(Locale.ENGLISH);
        if (os.contains("win"))
            return WINDOWS;

        if (os.contains("mac"))
            return MAC;

        if (os.contains("nux")
            || os.contains("nix")
            || os.contains("aix"))
            return LINUX;

        return UNKNOWN;
    }

    /**
     * Checks if the current operating system is Mac.
     *
     * @return true if the current OS is Mac, false otherwise
     */
    public static boolean isMac() {
        return CURRENT == MAC;
    }

    /**
     * Checks if the current operating system is Windows.
     *
     * @return true if the current OS is Windows, false otherwise
     */
    public static boolean isWindows() {
        return CURRENT == WINDOWS;
    }

    /**
     * Checks if the current operating system is Linux.
     *
     * @return true if the current OS is Linux, false otherwise
     */
    public static boolean isLinux() {
        return CURRENT == LINUX;
    }

    /**
     * Checks if the current operating system is unknown or unsupported.
     *
     * @return true if the current OS is unknown, false otherwise
     */
    public static boolean isUnknown() {
        return CURRENT == UNKNOWN;
    }
}
