package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorConfig;
import dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorPane;
import dev.railroadide.railroad.plugin.spi.dto.Project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.stream.Stream;

public class JavaCodeEditorPane extends CodeEditorPane {
    private static final int SIGNATURE_SCAN_WINDOW = 2048;
    private static final Set<String> NON_CALLABLE_PREFIX_KEYWORDS = Set.of(
        "if",
        "for",
        "while",
        "switch",
        "catch",
        "synchronized",
        "try",
        "do"
    );


    public JavaCodeEditorPane(Project project, Path item, CodeEditorConfig config) {
        super(project, item, config);
    }

    public static String[] resolveSystemModules() {
        try {
            Path javaHome = Path.of(System.getProperty("java.home"));
            Path jmods = javaHome.resolve("jmods");
            if (Files.isDirectory(jmods)) {
                try (Stream<Path> stream = Files.list(jmods)) {
                    return stream
                        .map(Path::toString)
                        .toArray(String[]::new);
                }
            }
        } catch (Exception exception) {
            Railroad.LOGGER.warn("Unable to resolve system modules for Java analysis", exception);
        }

        return new String[0];
    }

    @Override
    protected boolean shouldRequestSignatureHelp(String text, int caret, boolean textChanged) {
        if (text == null || text.isEmpty() || caret < 0 || caret > text.length())
            return false;

        int openParen = findRelevantCallOpenParen(text, caret);
        if (openParen < 0)
            return false;

        if (textChanged)
            return true;

        if (activeSignatureHelp.get() != null)
            return true;

        if (caret == 0)
            return false;

        char previous = text.charAt(caret - 1);
        return previous == '(' || previous == ',' || Character.isJavaIdentifierPart(previous);
    }

    private static int findRelevantCallOpenParen(String text, int caret) {
        int start = Math.max(0, caret - SIGNATURE_SCAN_WINDOW);
        ArrayDeque<Integer> openParentheses = new ArrayDeque<>();
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;

        for (int index = start; index < caret; index++) {
            char current = text.charAt(index);
            char next = index + 1 < caret ? text.charAt(index + 1) : '\0';

            if (inLineComment) {
                if (current == '\n' || current == '\r')
                    inLineComment = false;
                continue;
            }

            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    inBlockComment = false;
                    index++;
                }
                continue;
            }

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (inChar) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '\'') {
                    inChar = false;
                }
                continue;
            }

            if (current == '/' && next == '/') {
                inLineComment = true;
                index++;
                continue;
            }

            if (current == '/' && next == '*') {
                inBlockComment = true;
                index++;
                continue;
            }

            if (current == '"') {
                inString = true;
                escaped = false;
                continue;
            }

            if (current == '\'') {
                inChar = true;
                escaped = false;
                continue;
            }

            if (current == '(') {
                openParentheses.addLast(index);
            } else if (current == ')' && !openParentheses.isEmpty()) {
                openParentheses.removeLast();
            }
        }

        while (!openParentheses.isEmpty()) {
            int candidate = openParentheses.removeLast();
            if (isCallablePrefix(text, candidate))
                return candidate;
        }

        return -1;
    }

    private static boolean isCallablePrefix(String text, int openParen) {
        int index = openParen - 1;
        while (index >= 0 && Character.isWhitespace(text.charAt(index))) {
            index--;
        }

        if (index < 0)
            return false;

        char current = text.charAt(index);
        if (current == ')' || current == ']' || current == '>')
            return true;

        if (!Character.isJavaIdentifierPart(current))
            return false;

        int start = index;
        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
            start--;
        }

        String word = text.substring(start, index + 1);
        return !NON_CALLABLE_PREFIX_KEYWORDS.contains(word);
    }
}
