package dev.railroadide.railroad.settings.ui;

import dev.railroadide.railroad.ui.RRTextField;
import org.jetbrains.annotations.Nullable;

public class TerminalCustomFontFamilyPane extends RRTextField {
    public TerminalCustomFontFamilyPane(@Nullable String value) {
        setLocalizedPlaceholder("railroad.settings.appearance.terminal.terminal_custom_font_family.placeholder");
        setText(value == null ? "" : value);
    }
}
