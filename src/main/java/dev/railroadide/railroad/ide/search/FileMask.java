package dev.railroadide.railroad.ide.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Restricts a project search to files whose project-relative path matches comma-separated globs, such as
 * {@code *.json, src/main/resources, !*.png}. A pattern prefixed with {@code !} excludes matching
 * files. Patterns without a {@code /} match the file name, others match the relative path of the file or of one of
 * its directories. In patterns,
 * {@code *} matches within one path segment, {@code **} matches across segments and {@code ?} matches one character.
 * Matching ignores letter case. A mask without inclusion patterns includes every file that is not excluded.
 *
 * @param text the mask as typed
 */
public record FileMask(String text) {
    private static final FileMask ALL = new FileMask("");

    /**
     * Validates the mask text.
     *
     * @param text the mask as typed
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public FileMask {
        Objects.requireNonNull(text, "text");
    }

    /**
     * Returns the mask that accepts every file.
     *
     * @return unrestricted mask
     */
    public static FileMask all() {
        return ALL;
    }

    /**
     * Compiles this mask into a reusable matcher.
     *
     * @return matcher for project-relative paths
     */
    public Matcher matcher() {
        List<Glob> includes = new ArrayList<>();
        List<Glob> excludes = new ArrayList<>();
        for (String part : text.split(",")) {
            String pattern = part.strip();
            boolean exclude = pattern.startsWith("!");
            if (exclude) {
                pattern = pattern.substring(1).strip();
            }

            if (pattern.isEmpty())
                continue;

            (exclude ? excludes : includes).add(Glob.compile(pattern));
        }

        return new Matcher(List.copyOf(includes), List.copyOf(excludes));
    }

    /**
     * Tests project-relative paths against a compiled {@link FileMask}. Instances are immutable and thread-safe.
     */
    public static final class Matcher {
        private final List<Glob> includes;
        private final List<Glob> excludes;

        private Matcher(List<Glob> includes, List<Glob> excludes) {
            this.includes = includes;
            this.excludes = excludes;
        }

        /**
         * Tests a file.
         *
         * @param relativePath path relative to the project root, with {@code /} separators
         * @return whether the file is searched
         */
        public boolean matches(String relativePath) {
            String path = relativePath.replace('\\', '/');
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (excludes.stream().anyMatch(glob -> glob.matches(path, name)))
                return false;

            return includes.isEmpty() || includes.stream().anyMatch(glob -> glob.matches(path, name));
        }
    }

    private record Glob(Pattern pattern, boolean wholePath) {
        private static Glob compile(String glob) {
            String normalized = glob.replace('\\', '/');
            if (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }

            var regex = new StringBuilder();
            for (int index = 0; index < normalized.length(); index++) {
                char character = normalized.charAt(index);
                if (character == '*' && index + 1 < normalized.length() && normalized.charAt(index + 1) == '*') {
                    index++;
                    boolean directory = index + 1 < normalized.length() && normalized.charAt(index + 1) == '/';
                    if (directory) {
                        // "**/" also matches no directories at all, so "**/lang/x" matches "lang/x".
                        index++;
                        regex.append("(?:.*/)?");
                    } else {
                        regex.append(".*");
                    }
                } else if (character == '*') {
                    regex.append("[^/]*");
                } else if (character == '?') {
                    regex.append("[^/]");
                } else {
                    regex.append(Pattern.quote(String.valueOf(character)));
                }
            }

            boolean wholePath = normalized.indexOf('/') >= 0;
            if (wholePath) {
                // A directory pattern such as "src/main/resources" also matches every file beneath it.
                regex.append("(?:/.*)?");
            }

            int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            return new Glob(Pattern.compile(regex.toString(), flags), wholePath);
        }

        private boolean matches(String path, String name) {
            return pattern.matcher(wholePath ? path : name).matches();
        }
    }
}
