package dev.railroadide.railroad.ide.syntaxhighlighting;

import dev.railroadide.railroad.Railroad;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexJavaSyntaxHighlighting {
    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        long start = System.currentTimeMillis();
        Matcher matcher = Java.PATTERN.matcher(text);
        int lastKwEnd = 0;
        var spansBuilder = new StyleSpansBuilder<Collection<String>>();
        while (matcher.find()) {
            String styleClass = getStyleClass(matcher);
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }

        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        StyleSpans<Collection<String>> spans = spansBuilder.create();
        Railroad.LOGGER.debug("Computed highlighting in {} ms", System.currentTimeMillis() - start);
        return spans;
    }

    private static @NotNull String getStyleClass(Matcher matcher) {
        String styleClass =
            matcher.group("IDENTIFIER") != null ? "identifier" :
                matcher.group("METHOD") != null ? "method" :
                    matcher.group("CLASS") != null ? "class" :
                        matcher.group("KEYWORD") != null ? "keyword" :
                            matcher.group("LITERAL") != null ? "literal" :
                                matcher.group("STRING") != null ? "string" :
                                    matcher.group("COMMENT") != null ? "comment" :
                                        matcher.group("NUMBER") != null ? "number" :
                                            matcher.group("OPERATOR") != null ? "operator" :
                                                matcher.group("PAREN") != null ? "paren" :
                                                    matcher.group("BRACE") != null ? "brace" :
                                                        matcher.group("BRACKET") != null ? "bracket" :
                                                            matcher.group("SEMICOLON") != null ? "semicolon" :
                                                                matcher.group("ANNOTATION") != null ? "annotation" :
                                                                    matcher.group("GENERIC") != null ? "generic" :
                                                                        matcher.group("LAMBDA") != null ? "lambda" :
                                                                            null;
        if (styleClass == null)
            throw new IllegalStateException("Unknown style class");

        return styleClass;
    }

    public static class Java {
        private static final String[] KEYWORDS = new String[]{
            "abstract", "assert", "boolean", "break", "byte",
            "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else",
            "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import",
            "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super",
            "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "var",
            "record", "sealed", "non-sealed", "permits", "with", "as",
            "yield", "module", "requires", "exports", "opens", "to",
            "uses", "provides", "open", "transitive"
        };

        private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
        private static final String PAREN_PATTERN = "[()]";
        private static final String BRACE_PATTERN = "[{}]";
        private static final String BRACKET_PATTERN = "[\\[\\]]";
        private static final String SEMICOLON_PATTERN = ";";
        private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"";
        private static final String COMMENT_PATTERN = "//[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/";
        private static final String NUMBER_PATTERN = "\\b\\d+(\\.\\d+)?[lLfFdD]?\\b";
        private static final String OPERATOR_PATTERN = "\\+|-|\\*|/|%|==|!=|<|<=|>|>=|&&|\\|\\||!|\\?|:|\\+=|-=|\\*=|/=|%=|&=|\\|=|\\^=|<<=|>>=|>>>=|&|\\||\\^|~|<<|>>|>>>";
        private static final String METHOD_PATTERN = "\\b\\w+\\(";
        private static final String CLASS_PATTERN = "\\b[A-Z]\\w*\\b";
        private static final String IDENTIFIER_PATTERN = "[a-zA-Z_]\\w*";
        private static final String ANNOTATION_PATTERN = "@[a-zA-Z_]\\w*";
        private static final String GENERIC_PATTERN = "<[^<>]*>";
        private static final String LAMBDA_PATTERN = "\\([^()]*\\)\\s*->";
        private static final String LITERAL_PATTERN = "\\b(true|false|null)\\b";

        private static final Pattern PATTERN = Pattern.compile(
            "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
                + "|(?<PAREN>" + PAREN_PATTERN + ")"
                + "|(?<BRACE>" + BRACE_PATTERN + ")"
                + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
                + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
                + "|(?<STRING>" + STRING_PATTERN + ")"
                + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
                + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
                + "|(?<OPERATOR>" + OPERATOR_PATTERN + ")"
                + "|(?<METHOD>" + METHOD_PATTERN + ")"
                + "|(?<CLASS>" + CLASS_PATTERN + ")"
                + "|(?<IDENTIFIER>" + IDENTIFIER_PATTERN + ")"
                + "|(?<ANNOTATION>" + ANNOTATION_PATTERN + ")"
                + "|(?<GENERIC>" + GENERIC_PATTERN + ")"
                + "|(?<LAMBDA>" + LAMBDA_PATTERN + ")"
                + "|(?<LITERAL>" + LITERAL_PATTERN + ")"
        );
    }
}
