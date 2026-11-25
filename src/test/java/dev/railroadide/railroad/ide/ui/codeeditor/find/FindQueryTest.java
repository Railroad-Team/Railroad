package dev.railroadide.railroad.ide.ui.codeeditor.find;

import dev.railroadide.railroad.ide.sst.document.api.TextRange;
import dev.railroadide.railroad.plugin.spi.editor.TextReplacement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.PatternSyntaxException;

import static org.junit.jupiter.api.Assertions.*;

public class FindQueryTest {
    @Test
    public void literalSearchIgnoresCaseUnlessMatchCaseIsSet() {
        String text = "Foo foo FOO";

        assertEquals(List.of(range(0, 3), range(4, 7), range(8, 11)),
            new FindQuery("foo", false, false, false).findAll(text, 10));
        assertEquals(List.of(range(4, 7)), new FindQuery("foo", true, false, false).findAll(text, 10));
    }

    @Test
    public void literalSearchTreatsRegexMetacharactersAsText() {
        assertEquals(List.of(range(4, 7)), new FindQuery("a.b", true, false, false).findAll("axb a.b", 10));
        assertEquals(List.of(range(0, 2)), new FindQuery("$1", true, false, false).findAll("$1", 10));
    }

    @Test
    public void wholeWordsRejectMatchesThatContinueAWord() {
        String text = "foo food _foo foo_bar foo2 (foo) foo";

        assertEquals(List.of(range(0, 3), range(28, 31), range(33, 36)),
            new FindQuery("foo", true, true, false).findAll(text, 10));
    }

    @Test
    public void wholeWordsOnlyRequireBoundariesWhereTheQueryEndsInAWordCharacter() {
        assertEquals(List.of(range(1, 6)), new FindQuery(".call", true, true, false).findAll("x.call()", 10));
        assertTrue(new FindQuery(".cal", true, true, false).findAll("x.call()", 10).isEmpty());
    }

    @Test
    public void wholeWordsTreatUnicodeLettersAsWordCharacters() {
        assertTrue(new FindQuery("na", true, true, false).findAll("naïve", 10).isEmpty());
        assertEquals(List.of(range(0, 5)), new FindQuery("NAÏVE", false, true, false).findAll("naïve", 10));
    }

    @Test
    public void regexSearchUsesMultilineAnchorsAndSkipsEmptyMatches() {
        String text = "one\ntwo\n\nthree";

        assertEquals(List.of(range(0, 1), range(4, 5), range(9, 10)),
            new FindQuery("^.", true, false, true).findAll(text, 10));
        assertTrue(new FindQuery("x*", true, false, true).findAll(text, 10).isEmpty());
        assertEquals(List.of(range(3, 4), range(7, 9)), new FindQuery("\\n+", true, false, true).findAll(text, 10));
    }

    @Test
    public void regexWholeWordsApplyToTheMatchedText() {
        assertEquals(List.of(range(7, 10)), new FindQuery("b\\w+", true, true, true).findAll("abc ab bcd", 10));
    }

    @Test
    public void findAllStopsAtTheLimit() {
        assertEquals(List.of(range(0, 1), range(1, 2)), new FindQuery("a", true, false, false).findAll("aaaa", 2));
        assertTrue(new FindQuery("a", true, false, false).findAll("aaaa", 0).isEmpty());
    }

    @Test
    public void emptyQueryMatchesNothing() {
        var query = new FindQuery("", false, false, true);

        assertTrue(query.isEmpty());
        assertTrue(query.findAll("text", 10).isEmpty());
        assertTrue(query.replaceAll("text", "x").isEmpty());
    }

    @Test
    public void invalidRegexIsReported() {
        var query = new FindQuery("(", false, false, true);

        assertThrows(PatternSyntaxException.class, () -> query.findAll("(", 10));
        assertEquals(List.of(range(0, 1)), new FindQuery("(", false, false, false).findAll("(", 10));
    }

    @Test
    public void regexReplacementsExpandGroupsPerMatch() {
        var query = new FindQuery("(\\w+)=(\\d+)", true, false, true);

        assertEquals(List.of(edit(0, 3, "1:a"), edit(5, 10, "22:bb"), edit(12, 19, "333:ccc")),
            query.replaceAll("a=1, bb=22, ccc=333", "$2:$1"));
        assertEquals(List.of(edit(0, 3, "<x>")),
            new FindQuery("(?<name>x)=1", true, false, true).replaceAll("x=1", "<${name}>"));
    }

    @Test
    public void literalReplacementsInsertDollarSignsAndBackslashesVerbatim() {
        var query = new FindQuery("a", true, false, false);

        assertEquals(List.of(edit(0, 1, "$1\\n"), edit(2, 3, "$1\\n")), query.replaceAll("a a", "$1\\n"));
    }

    @Test
    public void replacementsSkipRejectedWholeWordCandidates() {
        var query = new FindQuery("(o+)", true, true, true);

        assertEquals(List.of(edit(4, 6, "[oo]")), query.replaceAll("foo oo", "[$1]"));
    }

    @Test
    public void invalidRegexReplacementIsReported() {
        var query = new FindQuery("(a)", true, false, true);

        assertThrows(IndexOutOfBoundsException.class, () -> query.replaceAll("a", "$2"));
        assertThrows(IllegalArgumentException.class, () -> query.replaceAll("a", "\\"));
        assertThrows(IllegalArgumentException.class, () -> query.replaceAll("a", "${missing}"));
    }

    @Test
    public void replaceComputesOnlyTheMatchAtTheGivenRange() {
        var query = new FindQuery("(\\d)", true, false, true);
        String text = "1 2 3";

        assertEquals(edit(2, 3, "<2>"), query.replace(text, range(2, 3), "<$1>"));
        assertNull(query.replace(text, range(1, 2), "<$1>"));
        assertNull(query.replace(text, range(2, 4), "<$1>"));
    }

    private static TextRange range(int start, int end) {
        return new TextRange(start, end);
    }

    private static TextReplacement edit(int start, int end, String text) {
        return new TextReplacement(range(start, end), text);
    }
}
