package dev.railroadide.railroad.ide.sst.impl.java;

/**
 * Unicode escapes of Java source text, translated as the compiler translates them before lexing (JLS 3.3).
 * <p>
 * A backslash starts an escape only when an even number of contiguous backslashes precedes it in the raw text and it
 * is followed by one or more {@code u} characters and four hexadecimal digits. Syntax tokens keep their raw text, so
 * features comparing identifier names translate it with {@link #translate(CharSequence)}.
 */
public final class JavaUnicodeEscapes {
    private JavaUnicodeEscapes() {
    }

    /**
     * Translates every Unicode escape of a text.
     *
     * @param text raw source text, such as the text of one token
     * @return the text with each escape replaced by the UTF-16 code unit it denotes; the same characters when the
     *         text contains no escape
     */
    public static String translate(CharSequence text) {
        if (!containsBackslash(text))
            return text.toString();

        var translated = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            int end = escapeEnd(text, index);
            if (end > 0) {
                translated.append(decoded(text, end));
                index = end - 1;
            } else {
                translated.append(text.charAt(index));
            }
        }

        return translated.toString();
    }

    /**
     * Returns where the Unicode escape starting at an index ends.
     *
     * @param text raw source text
     * @param index index of a possible escape's backslash
     * @return index just after the escape's last hexadecimal digit, or {@code -1} when no escape starts at the index
     */
    public static int escapeEnd(CharSequence text, int index) {
        if (index >= text.length() || text.charAt(index) != '\\')
            return -1;

        int backslashes = 0;
        for (int previous = index - 1; previous >= 0 && text.charAt(previous) == '\\'; previous--) {
            backslashes++;
        }

        if (backslashes % 2 != 0)
            return -1;

        int digits = index + 1;
        if (digits >= text.length() || text.charAt(digits) != 'u')
            return -1;

        while (digits < text.length() && text.charAt(digits) == 'u') {
            digits++;
        }

        if (digits + 4 > text.length())
            return -1;

        for (int digit = digits; digit < digits + 4; digit++) {
            if (Character.digit(text.charAt(digit), 16) < 0)
                return -1;
        }

        return digits + 4;
    }

    /**
     * Returns the character an escape denotes.
     *
     * @param text raw source text
     * @param end value returned by {@link #escapeEnd(CharSequence, int)} for the escape
     * @return the UTF-16 code unit of the escape's four hexadecimal digits
     */
    public static char decoded(CharSequence text, int end) {
        return (char) Integer.parseInt(text.subSequence(end - 4, end).toString(), 16);
    }

    private static boolean containsBackslash(CharSequence text) {
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\\')
                return true;
        }

        return false;
    }
}
