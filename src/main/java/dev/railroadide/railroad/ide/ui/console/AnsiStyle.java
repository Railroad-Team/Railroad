package dev.railroadide.railroad.ide.ui.console;

import java.util.ArrayList;
import java.util.List;

/**
 * Text attributes selected by ANSI SGR sequences. Colours are indices into the 16-colour ANSI palette, or
 * {@link #DEFAULT_COLOR} for the console's own colour.
 *
 * @param foreground foreground palette index
 * @param background background palette index
 * @param bold whether the text is bold
 * @param italic whether the text is italic
 * @param underline whether the text is underlined
 */
public record AnsiStyle(int foreground, int background, boolean bold, boolean italic, boolean underline) {
    /**
     * Palette index meaning the console's default colour.
     */
    public static final int DEFAULT_COLOR = -1;
    /**
     * Unstyled text.
     */
    public static final AnsiStyle PLAIN = new AnsiStyle(DEFAULT_COLOR, DEFAULT_COLOR, false, false, false);

    /**
     * Validates palette indices.
     *
     * @param foreground foreground palette index
     * @param background background palette index
     * @param bold whether the text is bold
     * @param italic whether the text is italic
     * @param underline whether the text is underlined
     */
    public AnsiStyle {
        if (foreground < DEFAULT_COLOR || foreground > 15)
            throw new IllegalArgumentException("foreground must be a 16-colour palette index");
        if (background < DEFAULT_COLOR || background > 15)
            throw new IllegalArgumentException("background must be a 16-colour palette index");
    }

    /**
     * Returns the CSS style classes that render this style.
     *
     * @return style classes such as {@code ansi-fg-1} and {@code ansi-bold}
     */
    public List<String> styleClasses() {
        if (equals(PLAIN))
            return List.of();

        var classes = new ArrayList<String>(4);
        if (foreground != DEFAULT_COLOR) {
            classes.add("ansi-fg-" + foreground);
        }
        if (background != DEFAULT_COLOR) {
            classes.add("ansi-bg-" + background);
        }
        if (bold) {
            classes.add("ansi-bold");
        }
        if (italic) {
            classes.add("ansi-italic");
        }
        if (underline) {
            classes.add("ansi-underline");
        }
        return classes;
    }

    /**
     * Returns a copy of this style with a different foreground.
     *
     * @param color the foreground palette index, or {@link #DEFAULT_COLOR}
     * @return the changed style
     */
    public AnsiStyle withForeground(int color) {
        return new AnsiStyle(color, background, bold, italic, underline);
    }

    /**
     * Returns a copy of this style with a different background.
     *
     * @param color the background palette index, or {@link #DEFAULT_COLOR}
     * @return the changed style
     */
    public AnsiStyle withBackground(int color) {
        return new AnsiStyle(foreground, color, bold, italic, underline);
    }

    /**
     * Returns a copy of this style with a different bold flag.
     *
     * @param value whether the text is bold
     * @return the changed style
     */
    public AnsiStyle withBold(boolean value) {
        return new AnsiStyle(foreground, background, value, italic, underline);
    }

    /**
     * Returns a copy of this style with a different italic flag.
     *
     * @param value whether the text is italic
     * @return the changed style
     */
    public AnsiStyle withItalic(boolean value) {
        return new AnsiStyle(foreground, background, bold, value, underline);
    }

    /**
     * Returns a copy of this style with a different underline flag.
     *
     * @param value whether the text is underlined
     * @return the changed style
     */
    public AnsiStyle withUnderline(boolean value) {
        return new AnsiStyle(foreground, background, bold, italic, value);
    }
}
