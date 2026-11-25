package dev.railroadide.railroad.ide.ui.console;

import dev.railroadide.railroad.ide.ui.console.AnsiParser.CarriageReturn;
import dev.railroadide.railroad.ide.ui.console.AnsiParser.Text;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnsiParserTest {
    private static final AnsiStyle RED = AnsiStyle.PLAIN.withForeground(1);

    @Test
    public void plainTextPassesThrough() {
        assertEquals(List.of(new Text("hello\n\tworld", AnsiStyle.PLAIN)), new AnsiParser().feed("hello\n\tworld"));
    }

    @Test
    public void selectGraphicRenditionStylesFollowingText() {
        List<AnsiParser.Token> tokens = new AnsiParser().feed("a\u001B[31mb\u001B[1;4mc\u001B[0md");

        assertEquals(List.of(
            new Text("a", AnsiStyle.PLAIN),
            new Text("b", RED),
            new Text("c", RED.withBold(true).withUnderline(true)),
            new Text("d", AnsiStyle.PLAIN)), tokens);
    }

    @Test
    public void emptyParametersReset() {
        var parser = new AnsiParser();
        parser.feed("\u001B[32m");

        assertEquals(List.of(new Text("x", AnsiStyle.PLAIN)), parser.feed("\u001B[mx"));
    }

    @Test
    public void escapeSequenceSplitAcrossChunksIsCompleted() {
        var parser = new AnsiParser();

        assertEquals(List.of(new Text("a", AnsiStyle.PLAIN)), parser.feed("a\u001B"));
        assertEquals(List.of(), parser.feed("[3"));
        assertEquals(List.of(new Text("b", RED)), parser.feed("1mb"));
    }

    @Test
    public void styleCarriesOverBetweenChunks() {
        var parser = new AnsiParser();
        parser.feed("\u001B[91m");

        assertEquals(List.of(new Text("next", AnsiStyle.PLAIN.withForeground(9))), parser.feed("next"));
    }

    @Test
    public void nonColourSequencesAndControlCharactersAreDiscarded() {
        List<AnsiParser.Token> tokens = new AnsiParser()
            .feed("a\u001B[2K\u001B[?25lb\u001B]0;title\u0007c\u001B]8;;url\u001B\\d\u001B(Be\bf");

        assertEquals(List.of(new Text("abcdef", AnsiStyle.PLAIN)), tokens);
    }

    @Test
    public void extendedColoursMapToNearestPaletteColour() {
        var parser = new AnsiParser();

        assertEquals(List.of(new Text("a", AnsiStyle.PLAIN.withForeground(9))), parser.feed("\u001B[38;5;196ma"));
        assertEquals(List.of(new Text("b", AnsiStyle.PLAIN.withForeground(9).withBackground(4))),
            parser.feed("\u001B[48;2;0;0;230mb"));
        assertEquals(List.of(new Text("c", AnsiStyle.PLAIN.withForeground(15).withBackground(4))),
            parser.feed("\u001B[38:5:231mc"));
    }

    @Test
    public void palette256CoversBasicCubeAndGreyscale() {
        assertEquals(3, foreground256(3));
        assertEquals(0, foreground256(16));
        assertEquals(15, foreground256(231));
        assertEquals(0, foreground256(232));
        assertEquals(7, foreground256(255));
    }

    @Test
    public void truncatedExtendedColourIsIgnored() {
        var parser = new AnsiParser();
        parser.feed("\u001B[38;5m");

        assertEquals(AnsiStyle.PLAIN, parser.style());
    }

    @Test
    public void crlfIsALineBreakEvenWhenSplit() {
        var parser = new AnsiParser();

        assertEquals(List.of(new Text("one", AnsiStyle.PLAIN)), parser.feed("one\r"));
        assertEquals(List.of(new Text("\ntwo", AnsiStyle.PLAIN)), parser.feed("\ntwo"));
    }

    @Test
    public void loneCarriageReturnRewritesLine() {
        List<AnsiParser.Token> tokens = new AnsiParser().feed("10%\r20%\r\n");

        assertEquals(List.of(
            new Text("10%", AnsiStyle.PLAIN),
            CarriageReturn.INSTANCE,
            new Text("20%\n", AnsiStyle.PLAIN)), tokens);
    }

    @Test
    public void runawayControlSequenceIsAbandoned() {
        List<AnsiParser.Token> tokens = new AnsiParser().feed("\u001B[" + "1".repeat(300) + "visible");

        assertEquals(List.of(new Text("1".repeat(43) + "visible", AnsiStyle.PLAIN)), tokens);
    }

    @Test
    public void resetForgetsStyleAndPartialSequence() {
        var parser = new AnsiParser();
        parser.feed("\u001B[31m\u001B[");
        parser.reset();

        assertEquals(List.of(new Text("x", AnsiStyle.PLAIN)), parser.feed("x"));
    }

    @Test
    public void styleClassesNameEachAttribute() {
        assertEquals(List.of(), AnsiStyle.PLAIN.styleClasses());
        assertEquals(List.of("ansi-fg-1", "ansi-bg-12", "ansi-bold", "ansi-italic", "ansi-underline"),
            new AnsiStyle(1, 12, true, true, true).styleClasses());
    }

    private static int foreground256(int index) {
        var parser = new AnsiParser();
        parser.feed("[38;5;" + index + "m");
        return parser.style().foreground();
    }
}
