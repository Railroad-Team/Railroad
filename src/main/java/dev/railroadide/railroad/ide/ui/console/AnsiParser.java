package dev.railroadide.railroad.ide.ui.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Splits terminal output into styled text, applying ANSI SGR sequences and discarding other escape sequences.
 * <p>
 * The parser is stateful: an escape sequence or a {@code \r\n} pair split across chunks is completed by the next call,
 * and the current style carries over. 256-colour and true-colour selections are mapped to the nearest colour of the
 * 16-colour palette so they follow the console theme. A lone carriage return is reported as a {@link CarriageReturn}
 * so the caller can overwrite the current line. Instances are not thread-safe.
 */
public final class AnsiParser {
    private static final char ESCAPE = '\u001B';
    private static final char BELL = '\u0007';
    private static final int MAX_SEQUENCE_LENGTH = 256;
    private static final int[][] PALETTE = {
        {0, 0, 0}, {205, 0, 0}, {0, 205, 0}, {205, 205, 0},
        {0, 0, 238}, {205, 0, 205}, {0, 205, 205}, {229, 229, 229},
        {127, 127, 127}, {255, 0, 0}, {0, 255, 0}, {255, 255, 0},
        {92, 92, 255}, {255, 0, 255}, {0, 255, 255}, {255, 255, 255}
    };

    private static final int[] CUBE_LEVELS = {0, 95, 135, 175, 215, 255};

    private final StringBuilder sequence = new StringBuilder();
    private State state = State.TEXT;
    private AnsiStyle style = AnsiStyle.PLAIN;
    private boolean pendingCarriageReturn;

    /**
     * Parses the next chunk of output.
     *
     * @param input the chunk to parse
     * @return the styled text and carriage returns it contains, in order; adjacent text with the same style is merged
     */
    public List<Token> feed(CharSequence input) {
        Objects.requireNonNull(input, "input");
        List<Token> tokens = new ArrayList<Token>();
        var text = new StringBuilder();
        for (int index = 0; index < input.length(); index++) {
            char character = input.charAt(index);
            switch (state) {
                case TEXT -> {
                    if (pendingCarriageReturn) {
                        pendingCarriageReturn = false;
                        if (character == '\n') {
                            text.append('\n');
                            continue;
                        }

                        flush(text, tokens);
                        tokens.add(CarriageReturn.INSTANCE);
                    }

                    readText(character, text, tokens);
                }

                case ESCAPE -> state = switch (character) {
                    case '[' -> {
                        sequence.setLength(0);
                        yield State.CSI;
                    }

                    case ']' -> State.OSC;
                    case '(', ')', '*', '+' -> State.CHARSET;
                    default -> State.TEXT;
                };

                case CHARSET -> state = State.TEXT;
                case CSI -> readControlSequence(character, text, tokens);
                case OSC -> {
                    if (character == BELL) {
                        state = State.TEXT;
                    } else if (character == ESCAPE) {
                        state = State.OSC_ESCAPE;
                    }
                }

                case OSC_ESCAPE -> state = character == '\\' ? State.TEXT : State.OSC;
            }
        }

        flush(text, tokens);
        return tokens;
    }

    /**
     * Returns the style that applies to the next text.
     *
     * @return the current style
     */
    public AnsiStyle style() {
        return style;
    }

    /**
     * Forgets the current style and any incomplete sequence.
     */
    public void reset() {
        state = State.TEXT;
        style = AnsiStyle.PLAIN;
        pendingCarriageReturn = false;
        sequence.setLength(0);
    }

    private void readText(char character, StringBuilder text, List<Token> tokens) {
        switch (character) {
            case ESCAPE -> {
                flush(text, tokens);
                state = State.ESCAPE;
            }

            case '\r' -> pendingCarriageReturn = true;
            case '\n', '\t' -> text.append(character);
            default -> {
                if (character >= ' ' && character != '\u007F') {
                    text.append(character);
                }
            }
        }
    }

    private void readControlSequence(char character, StringBuilder text, List<Token> tokens) {
        if (character >= '@' && character <= '~') {
            if (character == 'm') {
                flush(text, tokens);
                applySelectGraphicRendition(sequence.toString());
            }

            state = State.TEXT;
        } else if (sequence.length() >= MAX_SEQUENCE_LENGTH) {
            state = State.TEXT;
        } else {
            sequence.append(character);
        }
    }

    private void flush(StringBuilder text, List<Token> tokens) {
        if (text.isEmpty())
            return;

        if (!tokens.isEmpty() && tokens.getLast() instanceof Text(String previous, AnsiStyle previousStyle)
            && previousStyle.equals(style)) {
            tokens.set(tokens.size() - 1, new Text(previous + text, style));
        } else {
            tokens.add(new Text(text.toString(), style));
        }

        text.setLength(0);
    }

    private void applySelectGraphicRendition(String parameters) {
        if (parameters.isEmpty()) {
            style = AnsiStyle.PLAIN;
            return;
        }

        if (!Character.isDigit(parameters.charAt(0)) && parameters.charAt(0) != ';')
            return;

        String[] codes = parameters.split("[;:]", -1);
        for (int index = 0; index < codes.length; index++) {
            int code = parseCode(codes[index]);
            switch (code) {
                case 0 -> style = AnsiStyle.PLAIN;
                case 1 -> style = style.withBold(true);
                case 3 -> style = style.withItalic(true);
                case 4 -> style = style.withUnderline(true);
                case 22 -> style = style.withBold(false);
                case 23 -> style = style.withItalic(false);
                case 24 -> style = style.withUnderline(false);
                case 39 -> style = style.withForeground(AnsiStyle.DEFAULT_COLOR);
                case 49 -> style = style.withBackground(AnsiStyle.DEFAULT_COLOR);
                case 38, 48 -> {
                    int consumed = extendedColorLength(codes, index);
                    if (consumed == 0)
                        return;

                    int color = extendedColor(codes, index);
                    style = code == 38 ? style.withForeground(color) : style.withBackground(color);
                    index += consumed;
                }

                default -> {
                    if (code >= 30 && code <= 37) {
                        style = style.withForeground(code - 30);
                    } else if (code >= 40 && code <= 47) {
                        style = style.withBackground(code - 40);
                    } else if (code >= 90 && code <= 97) {
                        style = style.withForeground(code - 90 + 8);
                    } else if (code >= 100 && code <= 107) {
                        style = style.withBackground(code - 100 + 8);
                    }
                }
            }
        }
    }

    private static int extendedColorLength(String[] codes, int index) {
        if (index + 1 >= codes.length)
            return 0;

        return switch (parseCode(codes[index + 1])) {
            case 5 -> index + 2 < codes.length ? 2 : 0;
            case 2 -> index + 4 < codes.length ? 4 : 0;
            default -> 0;
        };
    }

    private static int extendedColor(String[] codes, int index) {
        if (parseCode(codes[index + 1]) == 5)
            return paletteIndex(parseCode(codes[index + 2]));

        return nearestPaletteColor(
            channel(parseCode(codes[index + 2])),
            channel(parseCode(codes[index + 3])),
            channel(parseCode(codes[index + 4])));
    }

    /**
     * Maps a colour of the 256-colour palette to the nearest colour of the 16-colour palette.
     *
     * @param index 256-colour palette index
     * @return 16-colour palette index
     */
    private static int paletteIndex(int index) {
        int color = Math.clamp(index, 0, 255);
        if (color < 16)
            return color;

        if (color >= 232) {
            int level = 8 + (color - 232) * 10;
            return nearestPaletteColor(level, level, level);
        }

        int cube = color - 16;
        return nearestPaletteColor(CUBE_LEVELS[cube / 36], CUBE_LEVELS[cube / 6 % 6], CUBE_LEVELS[cube % 6]);
    }

    /**
     * Finds the 16-colour palette entry closest to an RGB colour.
     *
     * @param red red channel, 0 to 255
     * @param green green channel, 0 to 255
     * @param blue blue channel, 0 to 255
     * @return 16-colour palette index
     */
    private static int nearestPaletteColor(int red, int green, int blue) {
        int nearest = 0;
        int nearestDistance = Integer.MAX_VALUE;
        for (int index = 0; index < PALETTE.length; index++) {
            int[] candidate = PALETTE[index];
            int dr = candidate[0] - red;
            int dg = candidate[1] - green;
            int db = candidate[2] - blue;
            int distance = dr * dr + dg * dg + db * db;
            if (distance < nearestDistance) {
                nearest = index;
                nearestDistance = distance;
            }
        }

        return nearest;
    }

    private static int channel(int value) {
        return Math.clamp(value, 0, 255);
    }

    private static int parseCode(String code) {
        if (code.isEmpty())
            return 0;

        try {
            return Integer.parseInt(code);
        } catch (NumberFormatException _) {
            return -1;
        }
    }

    private enum State {
        TEXT,
        ESCAPE,
        CHARSET,
        CSI,
        OSC,
        OSC_ESCAPE
    }

    /**
     * Output produced by the parser.
     */
    public sealed interface Token permits Text, CarriageReturn {
    }

    /**
     * Text to display in one style.
     *
     * @param text the text, possibly containing line breaks and tabs
     * @param style the style to display it in
     */
    public record Text(String text, AnsiStyle style) implements Token {
    }

    /**
     * A carriage return not followed by a line feed: the rest of the current line is about to be rewritten.
     */
    public enum CarriageReturn implements Token {
        /**
         * The only instance.
         */
        INSTANCE
    }
}
