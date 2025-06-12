package io.github.railroad.ide;

import io.github.railroad.Railroad;
import io.github.railroad.ide.syntaxhighlighting.JsonSyntaxHighlighting;
import io.github.railroad.utility.ShutdownHooks;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.concurrent.Task;
import javafx.geometry.Point2D;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Popup;
import javafx.util.Pair;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JsonCodeEditorPane extends TextEditorPane {
    private final ExecutorService executor0 = Executors.newSingleThreadExecutor();

    private record ValidationEntry(int start, int end, Popup popup) {}
    private final ObservableMap<ValidationException, ValidationEntry> errors = FXCollections.observableHashMap();
    private Schema schema;
    private static final String DEFAULT_SCHEMA = """
            {
              "$schema": "http://json-schema.org/draft-07/schema",
              "type": "object",
              "properties": {
                "name": {"type": "string"},
                "version": {"type": "string"}
              },
              "required": ["name", "version"]
            }
            """;

    public JsonCodeEditorPane(Path item) {
        super(item);
        syntaxHighlight();
        autoInsertPairs();
        autoIndentOnEnter();

        loadDefaultSchema();
        setupSchemaValidation();
        errors.addListener((MapChangeListener<ValidationException, ValidationEntry>) change -> {
            if (change.wasRemoved()) {
                change.getValueRemoved().popup().hide();
            }
        });

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
        overlayErrorHighlights();
    }

    private void overlayErrorHighlights() {
        errors.values().forEach(entry -> addStyleClass(entry.start, entry.end, "error"));
    }

    private void addStyleClass(int start, int end, String style) {
        StyleSpans<Collection<String>> spans = getStyleSpans(start, end);
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>(spans.getSpanCount());
        for (var span : spans) {
            List<String> styles = new ArrayList<>(span.getStyle());
            if (!styles.contains(style)) {
                styles.add(style);
            }

            builder.add(styles, span.getLength());
        }

        setStyleSpans(start, builder.create());
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        return JsonSyntaxHighlighting.computeHighlighting(text);
    }

    private void clearErrorHighlights() {
        int length = getLength();
        setStyle(0, length, Collections.emptyList());
        applyHighlighting(computeHighlighting(getText()));
        errors.values().forEach(entry -> entry.popup.hide());
        errors.clear();
    }

    private void highlightValidationErrors(ValidationException rootEx) {
        var exceptions = rootEx.getCausingExceptions();
        if (exceptions.isEmpty()) {
            exceptions = List.of(rootEx);
        }

        String text = getText();
        for (ValidationException ex : exceptions) {
            Pair<Integer, Integer> range = findRangeForPointer(ex.getPointerToViolation(), text);
            int start = range.getKey();
            int end = range.getValue();
            if (start < 0 || end <= start) {
                continue;
            }

            addStyleClass(start, end, "error");

            var popup = new Popup();
            var label = new Label(ex.getMessage());
            label.getStyleClass().add("diagnostic-pane");
            popup.getContent().add(label);

            addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN, event -> {
                int pos = event.getCharacterIndex();
                if (pos >= start && pos <= end) {
                    Point2D screen = event.getScreenPosition();
                    popup.show(JsonCodeEditorPane.this, screen.getX(), screen.getY());
                }
            });

            addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
                if (!popup.isShowing())
                    return;

                int pos = JsonCodeEditorPane.this.hit(event.getX(), event.getY())
                        .getCharacterIndex().orElse(-1);
                if (pos < start || pos > end) {
                    popup.hide();
                }
            });

            errors.put(ex, new ValidationEntry(start, end, popup));
        }
    }

    private Pair<Integer, Integer> findRangeForPointer(String pointer, String text) {
        if (pointer == null || "#".equals(pointer)) {
            return new Pair<>(0, getLength());
        }

        String prop = pointer.substring(pointer.lastIndexOf('/') + 1)
                .replace("~1", "/")
                .replace("~0", "~");
        String search = "\"" + prop + "\"";
        int nameIdx = text.indexOf(search);
        if (nameIdx < 0) {
            return new Pair<>(-1, -1);
        }

        int colonIdx = text.indexOf(':', nameIdx + search.length());
        if (colonIdx < 0) {
            return new Pair<>(nameIdx, nameIdx + search.length());
        }

        int valueStart = colonIdx + 1;
        while (valueStart < text.length() && Character.isWhitespace(text.charAt(valueStart))) {
            valueStart++;
        }

        int valueEnd = valueStart;
        if (valueStart < text.length()) {
            char ch = text.charAt(valueStart);
            if (ch == '"') {
                valueEnd++;
                boolean escaped = false;
                while (valueEnd < text.length()) {
                    char c = text.charAt(valueEnd);
                    if (escaped) {
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        valueEnd++;
                        break;
                    }
                    valueEnd++;
                }
            } else if (ch == '{' || ch == '[') {
                char open = ch;
                char close = ch == '{' ? '}' : ']';
                int depth = 1;
                valueEnd++;
                while (valueEnd < text.length() && depth > 0) {
                    char c = text.charAt(valueEnd);
                    if (c == open)
                        depth++;
                    else if (c == close)
                        depth--;

                    valueEnd++;
                }
            } else {
                while (valueEnd < text.length()) {
                    char c = text.charAt(valueEnd);
                    if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                        break;
                    }

                    valueEnd++;
                }
            }
        }

        return new Pair<>(nameIdx, valueEnd);
    }

    private void loadDefaultSchema() { // TODO: Get rid of this hardcoded schema
        JSONObject raw = new JSONObject(new JSONTokener(DEFAULT_SCHEMA));
        this.schema = SchemaLoader.load(raw);
    }

    public void setSchema(JSONObject schemaJson) {
        this.schema = SchemaLoader.load(schemaJson);
        validateAndHighlight();
    }

    private void setupSchemaValidation() {
        textProperty().addListener((obs, oldText, newText) -> validateAndHighlight());
        validateAndHighlight();
    }

    private void validateAndHighlight() {
        if (schema == null)
            return;

        try {
            clearErrorHighlights();
            schema.validate(new JSONObject(getText()));
        } catch (ValidationException exception) {
            Railroad.LOGGER.error("JSON validation error: {}", exception.getMessage());
            highlightValidationErrors(exception);
        } catch (JSONException exception) {
            Railroad.LOGGER.error("JSON parse error: {}", exception.getMessage());
            addStyleClass(0, getLength(), "error");
        }
    }
}