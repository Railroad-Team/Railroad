package dev.railroadide.core.utility;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Enum representing the operating system on which the application is running.
 * It provides methods to detect the current operating system and retrieve it.
 */
public enum OperatingSystem {
    WINDOWS, MAC, LINUX, UNKNOWN;

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
}
