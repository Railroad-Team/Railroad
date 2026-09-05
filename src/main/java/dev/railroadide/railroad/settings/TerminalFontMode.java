package dev.railroadide.railroad.settings;

/** Selects how the terminal chooses its font. */
public enum TerminalFontMode {
    /** Choose the platform or terminal default automatically. */
    AUTO,
    /** Use a font installed on the system. */
    INSTALLED_FONT,
    /** Use a custom font family name. */
    CUSTOM_FAMILY
}
