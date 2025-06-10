package io.github.railroad.ide;

import io.github.railroad.Railroad;
import io.github.railroad.ide.syntaxhighlighting.JsonSyntaxHighlighting;
import io.github.railroad.utility.ShutdownHooks;
import javafx.concurrent.Task;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.fxmisc.richtext.model.StyleSpans;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JsonCodeEditorPane extends TextEditorPane {
    private final ExecutorService executor0 = Executors.newSingleThreadExecutor();

    public JsonCodeEditorPane(Path item) {
        super(item);
        syntaxHighlight();
        autoInsertPairs();
        autoIndentOnEnter();
        ShutdownHooks.addHook(executor0::shutdown);
    }

    private void syntaxHighlight() {
        applyHighlighting(computeHighlighting(getText()));
        multiPlainChanges()
                .successionEnds(Duration.ofMillis(5))
                .retainLatestUntilLater(executor0)
                .supplyTask(this::computeHighlightingAsync)
                .awaitLatest(multiPlainChanges())
                .filterMap(throwable -> {
                    if (throwable.isSuccess()) {
                        return throwable.toOptional();
                    } else {
                        Railroad.LOGGER.error("Failed to compute highlighting", throwable.getFailure());
                        return Optional.empty();
                    }
                })
                .subscribe(this::applyHighlighting);
    }

    private void autoInsertPairs() {
        addEventFilter(KeyEvent.KEY_TYPED, event -> {
            String ch = event.getCharacter();
            if (ch == null || ch.isEmpty()) return;

            String closing = switch (ch) {
                case "[" -> "]";
                case "{" -> "}";
                case "\"" -> "\"";
                default -> null;
            };

            if (closing != null) {
                int pos = getCaretPosition();
                boolean insideString = isInsideString(pos);
                boolean allowInside = Railroad.SETTINGS_HANDLER.getBooleanSetting("railroad:auto_pair_inside_strings");

                if (!insideString || allowInside) {
                    replaceText(pos, pos, ch + closing);
                    moveTo(pos + 1);
                    event.consume();
                }
            }
        });
    }

    private boolean isInsideString(int position) {
        String text = getText().substring(0, position);
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                inString = !inString;
            }
        }

        return inString;
    }


    private void autoIndentOnEnter() {
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != KeyCode.ENTER)
                return;

            int pos = getCaretPosition();
            String text = getText();

            String before = text.substring(0, pos);
            String after = text.substring(pos);

            int lineStart = before.lastIndexOf('\n') + 1;
            StringBuilder baseIndent = new StringBuilder();
            while (lineStart < before.length()) {
                char c = before.charAt(lineStart);
                if (c == ' ' || c == '\t') {
                    baseIndent.append(c);
                    lineStart++;
                } else {
                    break;
                }
            }

            char prev = pos > 0 ? before.charAt(pos - 1) : '\0';
            char next = !after.isEmpty() ? after.charAt(0) : '\0';

            boolean special = (prev == '{' && next == '}') || (prev == '[' && next == ']');

            if (special) {
                String indent = baseIndent.toString();
                String inner = indent + "    ";
                String insertion = "\n" + inner + "\n" + indent;
                replaceText(pos, pos, insertion);
                moveTo(pos + 1 + inner.length());
            } else {
                StringBuilder indent = new StringBuilder(baseIndent);
                if (prev == '{' || prev == '[') {
                    indent.append("    ");
                }
                if ((next == '}' || next == ']') && indent.length() >= 4 && prev != '{' && prev != '[') {
                    indent.setLength(indent.length() - 4);
                }

                String insertion = "\n" + indent;
                replaceText(pos, pos, insertion);
                moveTo(pos + insertion.length());
            }

            event.consume();
        });
    }

    private Task<StyleSpans<Collection<String>>> computeHighlightingAsync() {
        String text = getText();
        Task<StyleSpans<Collection<String>>> task = new Task<>() {
            @Override
            protected StyleSpans<Collection<String>> call() {
                return computeHighlighting(text);
            }
        };

        executor0.submit(task);
        return task;
    }

    private void applyHighlighting(StyleSpans<Collection<String>> highlighting) {
        setStyleSpans(0, highlighting);
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        return JsonSyntaxHighlighting.computeHighlighting(text);
    }
}