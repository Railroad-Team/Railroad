package dev.railroadide.railroad.ide.ui.codeeditor.find;

import dev.railroadide.railroad.ide.sst.document.api.TextRange;
import dev.railroadide.railroad.plugin.spi.editor.TextReplacement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Describes a find request within a single document and evaluates it against document text.
 * Empty matches, such as those of {@code ^} or {@code a*}, are never reported.
 *
 * @param text literal text or regular expression to search for
 * @param matchCase whether letter case must match exactly
 * @param wholeWords whether matches must not continue a word on either side
 * @param regex whether {@code text} is a regular expression and replacements may reference its groups
 */
public record FindQuery(String text, boolean matchCase, boolean wholeWords, boolean regex) {
    /**
     * Validates the query components.
     *
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public FindQuery {
        Objects.requireNonNull(text, "text");
    }

    /**
     * Reports whether this query can never match.
     *
     * @return whether the search text is empty
     */
    public boolean isEmpty() {
        return text.isEmpty();
    }

    /**
     * Compiles this query into the pattern used for matching.
     *
     * @return compiled pattern
     * @throws PatternSyntaxException if this is a regular-expression query with invalid syntax
     */
    public Pattern compile() {
        int flags = regex ? Pattern.MULTILINE : Pattern.LITERAL;
        if (!matchCase) {
            flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        }
        return Pattern.compile(text, flags);
    }

    /**
     * Finds non-overlapping matches in document order.
     *
     * @param document text to search
     * @param limit maximum number of matches to return
     * @return matching ranges, empty when the query is empty
     * @throws PatternSyntaxException if this is a regular-expression query with invalid syntax
     */
    public List<TextRange> findAll(CharSequence document, int limit) {
        var matches = new ArrayList<TextRange>();
        if (isEmpty() || limit <= 0)
            return matches;

        Matcher matcher = compile().matcher(document);
        while (matches.size() < limit && matcher.find()) {
            if (accepts(document, matcher)) {
                matches.add(new TextRange(matcher.start(), matcher.end()));
            }
        }
        return matches;
    }

    /**
     * Computes the replacement of every match. Regular-expression replacements may reference groups as
     * {@code $1} or {@code ${name}} and escape {@code $} or {@code \} with a backslash; otherwise the
     * replacement is inserted literally.
     *
     * @param document text to search
     * @param replacement replacement template
     * @return one replacement per match in document order
     * @throws PatternSyntaxException if this is a regular-expression query with invalid syntax
     * @throws IllegalArgumentException if the replacement references a missing group or ends in an escape
     * @throws IndexOutOfBoundsException if the replacement references a group index the pattern lacks
     */
    public List<TextReplacement> replaceAll(String document, String replacement) {
        return replacements(document, replacement, null);
    }

    /**
     * Computes the replacement of the match that occupies exactly the given range.
     *
     * @param document text to search
     * @param match range expected to be a match of this query
     * @param replacement replacement template, interpreted as by {@link #replaceAll(String, String)}
     * @return replacement, or {@code null} when the range is not a match
     * @throws PatternSyntaxException if this is a regular-expression query with invalid syntax
     * @throws IllegalArgumentException if the replacement references a missing group or ends in an escape
     * @throws IndexOutOfBoundsException if the replacement references a group index the pattern lacks
     */
    public TextReplacement replace(String document, TextRange match, String replacement) {
        Objects.requireNonNull(match, "match");
        List<TextReplacement> found = replacements(document, replacement, match);
        return found.isEmpty() ? null : found.getFirst();
    }

    private List<TextReplacement> replacements(String document, String replacement, TextRange only) {
        Objects.requireNonNull(replacement, "replacement");
        var replacements = new ArrayList<TextReplacement>();
        if (isEmpty())
            return replacements;

        String template = regex ? replacement : Matcher.quoteReplacement(replacement);
        Matcher matcher = compile().matcher(document);
        var expanded = new StringBuilder();
        int appendPosition = 0;
        while (matcher.find()) {
            if (only != null && matcher.start() > only.start)
                break;
            if (!accepts(document, matcher)
                || only != null && (matcher.start() != only.start || matcher.end() != only.end))
                continue;

            int expansionStart = matcher.start() - appendPosition;
            matcher.appendReplacement(expanded, template);
            var range = new TextRange(matcher.start(), matcher.end());
            replacements.add(new TextReplacement(range, expanded.substring(expansionStart)));
            expanded.setLength(0);
            appendPosition = matcher.end();
        }
        return replacements;
    }

    private boolean accepts(CharSequence document, Matcher matcher) {
        int start = matcher.start();
        int end = matcher.end();
        if (start == end)
            return false;
        if (!wholeWords)
            return true;

        boolean startsWord = isWordCharacter(document.charAt(start));
        boolean endsWord = isWordCharacter(document.charAt(end - 1));
        boolean joinedBefore = start > 0 && isWordCharacter(document.charAt(start - 1));
        boolean joinedAfter = end < document.length() && isWordCharacter(document.charAt(end));
        return !(startsWord && joinedBefore) && !(endsWord && joinedAfter);
    }

    private static boolean isWordCharacter(char character) {
        return character == '_' || Character.isLetterOrDigit(character);
    }
}
