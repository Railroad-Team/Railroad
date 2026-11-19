package dev.railroadide.railroad.ide.sst.provider.api;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

/** Returns positive specificity on a match, or zero. Priority is resolved by the runtime first. */
@FunctionalInterface
public interface LanguageMatcher {
    /**
     * Scores how specifically this matcher recognizes the input.
     *
     * @param input file path and bounded content prefix
     * @return a positive specificity score for a match, or zero for no match
     */
    int match(LanguageMatchInput input);

    /**
     * Creates a case-sensitive matcher for an exact filename.
     *
     * @param name complete filename to match
     * @return a matcher scoring matches as 400 plus the filename length
     */
    static LanguageMatcher exactName(String name) {
        Objects.requireNonNull(name, "name");
        return input -> input.fileName().equals(name) ? 400 + name.length() : 0;
    }

    /**
     * Recognizes single and compound suffixes, case-insensitively.
     *
     * @param extension filename extension with or without a leading dot
     * @return a matcher scoring matches as 200 plus the dot-prefixed suffix length
     */
    static LanguageMatcher extension(String extension) {
        Objects.requireNonNull(extension, "extension");
        if (extension.isBlank() || extension.equals("."))
            throw new IllegalArgumentException("Extension cannot be blank");
        String suffix = "." + extension.toLowerCase(Locale.ROOT).replaceFirst("^\\.", "");
        return input -> input.fileName().toLowerCase(Locale.ROOT).endsWith(suffix) ? 200 + suffix.length() : 0;
    }

    /**
     * Recognizes an interpreter in the first-line shebang, directly or through {@code env}.
     *
     * @param interpreter interpreter executable name without a directory
     * @return a matcher scoring recognized shebangs as 300
     */
    static LanguageMatcher shebang(String interpreter) {
        Objects.requireNonNull(interpreter, "interpreter");
        return input -> {
            String firstLine = input.textPrefix().lines().findFirst().orElse("");
            if (!firstLine.startsWith("#!"))
                return 0;
            String[] words = firstLine.substring(2).trim().split("\\s+");
            String executable = words[0].substring(words[0].lastIndexOf('/') + 1);
            if (executable.equals(interpreter))
                return 300;
            if (!executable.equals("env"))
                return 0;
            for (int i = 1; i < words.length; i++) {
                if (words[i].startsWith("-") || words[i].contains("="))
                    continue;
                return words[i].equals(interpreter) ? 300 : 0;
            }
            return 0;
        };
    }

    /**
     * Creates a matcher backed by a predicate over bounded recognition input.
     *
     * @param sniffer predicate testing the filename or captured content
     * @return a matcher scoring accepted input as 100
     */
    static LanguageMatcher content(Predicate<LanguageMatchInput> sniffer) {
        Objects.requireNonNull(sniffer, "sniffer");
        return input -> sniffer.test(input) ? 100 : 0;
    }

    /**
     * Combines matchers by taking their highest specificity score.
     *
     * @param matchers non-null matchers to copy and evaluate
     * @return a matcher returning the highest score, or zero for an empty matcher array
     */
    static LanguageMatcher any(LanguageMatcher... matchers) {
        LanguageMatcher[] copy = Arrays.copyOf(matchers, matchers.length);
        for (LanguageMatcher matcher : copy) {
            Objects.requireNonNull(matcher, "matcher");
        }
        return input -> Arrays.stream(copy).mapToInt(matcher -> matcher.match(input)).max().orElse(0);
    }
}
