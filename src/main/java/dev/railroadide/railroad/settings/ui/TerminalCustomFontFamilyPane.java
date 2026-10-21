package dev.railroadide.railroad.settings.ui;

import dev.railroadide.railroad.ui.RRTextField;
import org.jetbrains.annotations.Nullable;

/**
 * Text field for configuring the terminal's custom font family.
 */
public class TerminalCustomFontFamilyPane extends RRTextField {
    /**
     * Creates a terminal font family field initialized with the supplied value.
     *
     * @param value initial font family name, or {@code null} for an empty field
     */
    public TerminalCustomFontFamilyPane(@Nullable String value) {
        setLocalizedPlaceholder("railroad.settings.appearance.terminal.terminal_custom_font_family.placeholder");
        setText(value == null ? "" : value);
    }
}
