package io.github.railroad.ide.syntaxhighlighting;

import io.github.railroad.Railroad;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonSyntaxHighlighting {
    private static final String STRING = "\\\"(?:\\\\.|[^\\\"\\\\])*\\\"";
    private static final String NUMBER = "-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?";
    private static final String KEY = STRING + "(?=\\s*:)";
    private static final String BOOLEAN = "\\b(?:true|false)\\b";
    private static final String NULL = "\\bnull\\b";

    private static final Pattern PATTERN = Pattern.compile(
            "(?<KEY>" + KEY + ")"
                    + "|(?<STRING>" + STRING + ")"
                    + "|(?<NUMBER>" + NUMBER + ")"
                    + "|(?<BOOLEAN>" + BOOLEAN + ")"
                    + "|(?<NULL>" + NULL + ")"
    );

    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        long start = System.currentTimeMillis();
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        var spansBuilder = new StyleSpansBuilder<Collection<String>>();
        while (matcher.find()) {
            String styleClass =
                    matcher.group("KEY") != null ? "name" :
                            matcher.group("STRING") != null ? "string" :
                                    matcher.group("NUMBER") != null ? "number" :
                                            matcher.group("BOOLEAN") != null ? "keyword" :
                                                    matcher.group("NULL") != null ? "keyword" : null;
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        StyleSpans<Collection<String>> spans = spansBuilder.create();
        Railroad.LOGGER.debug("Computed JSON highlighting in {} ms", System.currentTimeMillis() - start);
        return spans;
    }
}