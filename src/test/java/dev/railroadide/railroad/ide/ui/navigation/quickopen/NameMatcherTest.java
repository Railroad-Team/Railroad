package dev.railroadide.railroad.ide.ui.navigation.quickopen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NameMatcherTest {
    @Test
    public void humpsMatchTheStartsOfCamelCaseWords() {
        NameMatcher.Match match = NameMatcher.match("FRB", "FindReplaceBar");

        assertNotNull(match);
        assertArrayEquals(new int[]{0, 4, 11}, match.positions());
    }

    @Test
    public void lowerCaseHumpsContinueWithinWords() {
        NameMatcher.Match match = NameMatcher.match("findrepb", "FindReplaceBar");

        assertNotNull(match);
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5, 6, 11}, match.positions());
    }

    @Test
    public void wordsStartAfterSeparatorsAndDigits() {
        NameMatcher.Match match = NameMatcher.match("mj", "src/main/java");

        assertNotNull(match);
        assertArrayEquals(new int[]{4, 9}, match.positions());
        assertTrue(NameMatcher.isWordStart("v2Beta", 2));
        assertTrue(NameMatcher.isWordStart("v2beta", 2));
        assertFalse(NameMatcher.isWordStart("ab", 1));
    }

    @Test
    public void subsequencesMatchWhenHumpsSkipNeededCharacters() {
        assertNotNull(NameMatcher.match("ndx", "FindIndex"));
        assertNotNull(NameMatcher.match("ace", "ReplaceBar"));
    }

    @Test
    public void missingCharactersDoNotMatch() {
        assertNull(NameMatcher.match("xyz", "FindReplaceBar"));
        assertNull(NameMatcher.match("barf", "FindReplaceBar"));
        assertNull(NameMatcher.match("LongerThanText", "Text"));
    }

    @Test
    public void emptyPatternMatchesEverything() {
        NameMatcher.Match match = NameMatcher.match("", "Anything");

        assertNotNull(match);
        assertEquals(0, match.score());
        assertEquals(0, match.positions().length);
    }

    @Test
    public void exactNamesOutrankPrefixesWhichOutrankScatteredMatches() {
        int exact = NameMatcher.match("main", "Main").score();
        int prefix = NameMatcher.match("main", "MainWindow").score();
        int scattered = NameMatcher.match("main", "ModAndIndex").score();

        assertTrue(exact > prefix, exact + " <= " + prefix);
        assertTrue(prefix > scattered, prefix + " <= " + scattered);
    }

    @Test
    public void matchesAtWordStartsOutrankMatchesInsideWords() {
        int wordStart = NameMatcher.match("bar", "FooBar").score();
        int insideWord = NameMatcher.match("bar", "Foobar").score();

        assertTrue(wordStart > insideWord, wordStart + " <= " + insideWord);
    }
}
