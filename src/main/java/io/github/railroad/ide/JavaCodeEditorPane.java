package io.github.railroad.ide;

import io.github.palexdev.mfxresources.fonts.MFXFontIcon;
import io.github.palexdev.mfxresources.fonts.fontawesome.FontAwesomeSolid;
import io.github.railroad.Railroad;
import io.github.railroad.ide.classparser.stub.ClassStub;
import io.github.railroad.ide.indexing.Autocomplete;
import io.github.railroad.ide.indexing.Indexes;
import io.github.railroad.ide.syntaxhighlighting.TreeSitterJavaSyntaxHighlighting;
import io.github.railroad.ui.defaults.RRListView;
import io.github.railroad.utility.ShutdownHooks;
import io.github.railroad.utility.compiler.JavaSourceFromString;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.concurrent.Task;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Popup;
import javafx.util.Pair;
import org.eclipse.jdt.core.dom.*;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxmisc.richtext.model.StyleSpans;
import org.jetbrains.annotations.Nullable;

import javax.tools.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

public class JavaCodeEditorPane extends TextEditorPane {
    private static final JavaCompiler JAVA_COMPILER = ToolProvider.getSystemJavaCompiler();

    private final ExecutorService executor0 = Executors.newFixedThreadPool(2);
    private final ObservableMap<Diagnostic<? extends JavaFileObject>, Popup> errors = FXCollections.observableHashMap();
    private final Map<Integer, Diagnostic.Kind> lineToSeverity = new HashMap<>();

    private final List<ClassStub> stubs = Indexes.scanStandardLibrary();
    private final Autocomplete autocomplete = new Autocomplete(stubs);
    private final AtomicReference<Popup> autoCompletePopup = new AtomicReference<>(null);
    private int dotPosition = -1;
    private final List<String> fullSuggestions = new ArrayList<>();
    private ChangeListener<String> textListener;

    public JavaCodeEditorPane(Path item) {
        super(item);

        marginErrors();

        syntaxHighlight();
        errorHighlighting();
        codeCompletion();
        highlightBracketPairs();

        ShutdownHooks.addHook(executor0::shutdown);
    }

    private void highlightBracketPairs() {
        caretPositionProperty().addListener((observable, oldValue, newValue) -> {
            String text = getText();
            if(text.isBlank() || newValue < 0 || newValue > text.length())
                return;

            Map<Character, Character> bracketPairs = Map.of(
                    '(', ')',
                    '{', '}',
                    '[', ']',
                    ')', '(',
                    '}', '{',
                    ']', '['
            );

            char currentChar = newValue < text.length() ? text.charAt(newValue) : '\0';
            if(!bracketPairs.containsKey(currentChar) && newValue > 0) {
                currentChar = text.charAt(newValue - 1);
                newValue--;
            }

            if(!bracketPairs.containsKey(currentChar)) {
                clearBracketHighlights();
                return;
            }

            boolean forward = (currentChar == '(' || currentChar == '{' || currentChar == '[');

            highlightMatchingBracket(text, newValue, currentChar, bracketPairs.get(currentChar), forward);
        });
    }

    private void highlightMatchingBracket(String text, int position, char currentChar, char matchingBracket, boolean lookForward) {
        int matchPos = findMatchingBracketPosition(text, position, currentChar, matchingBracket, lookForward);
        clearBracketHighlights();

        if(matchPos != -1) {
            setStyleClass(position, position + 1, "bracket-highlight");
            setStyleClass(matchPos, matchPos + 1, "bracket-highlight");
        }
    }

    private int findMatchingBracketPosition(String text, int pos, char open, char close, boolean forward) {
        int balance = 0;
        int length = text.length();

        if (forward) {
            for (int index = pos; index < length; index++) {
                char c = text.charAt(index);
                if (c == open)
                    balance++;
                else if (c == close)
                    balance--;

                if (balance == 0)
                    return index;
            }
        } else {
            for (int index = pos; index >= 0; index--) {
                char c = text.charAt(index);
                if (c == open)
                    balance--;
                else if (c == close)
                    balance++;

                if (balance == 0)
                    return index;
            }
        }

        return -1;
    }

    private void clearBracketHighlights() {
        int length = getLength();
        setStyle(0, length, Collections.emptyList());
        applyHighlighting(computeHighlighting(getText()));
    }

    private void marginErrors() {
        IntFunction<Node> factory = line -> {
            // Create GridPane with two columns
            var grid = new GridPane();
            grid.setHgap(5); // Horizontal spacing between columns
            grid.getStyleClass().add("ide-java-code-editor-grid");

            // Column for line numbers (expands to fill space)
            var lineNumberColumn = new ColumnConstraints();
            lineNumberColumn.setHgrow(Priority.ALWAYS); // Grows to fill remaining space

            // Column for icons (fixed width)
            var iconColumn = new ColumnConstraints();
            iconColumn.setPrefWidth(12); // Fixed width matching icon size
            iconColumn.setHgrow(Priority.NEVER); // Prevents growing

            grid.getColumnConstraints().addAll(lineNumberColumn, iconColumn);

            // Line number label
            var lineNumber = new Label(String.format("%4d", line + 1));
            lineNumber.setTextAlignment(TextAlignment.RIGHT); // Right-align the line number
            lineNumber.setTextFill(Color.LIGHTGRAY); // Optional: lighter text for contrast
            grid.add(lineNumber, 0, 0); // Place in first column

            // Add icon and tooltip if there's an error or warning
            Diagnostic.Kind kind = lineToSeverity.get(line + 1); // 1-based line numbers
            if (kind != null) {
                var icon = new MFXFontIcon(kind == Diagnostic.Kind.ERROR ?
                        FontAwesomeSolid.CIRCLE_EXCLAMATION : FontAwesomeSolid.TRIANGLE_EXCLAMATION,
                        12,
                        kind == Diagnostic.Kind.ERROR ? Color.RED : Color.YELLOW);
                grid.add(icon, 1, 0); // Place in second column

                // Find the diagnostic message for this line
                String message = errors.keySet().stream()
                        .filter(d -> d.getLineNumber() == line + 1 &&
                                (d.getKind() == kind || (kind == Diagnostic.Kind.WARNING &&
                                        d.getKind() == Diagnostic.Kind.MANDATORY_WARNING)))
                        .map(d -> d.getMessage(null))
                        .findFirst()
                        .orElse("Unknown issue");

                // Add tooltip to the icon
                var tooltip = new Tooltip(message);
                tooltip.setShowDelay(javafx.util.Duration.millis(200)); // Slight delay for smoother UX
                Tooltip.install(icon, tooltip);
            }

            return grid;
        };

        setParagraphGraphicFactory(factory);
    }

    private void codeCompletion() {
        plainTextChanges()
                .successionEnds(Duration.ofMillis(500))
                .retainLatestUntilLater(executor0)
                .filter(change -> !change.getInserted().equals(change.getRemoved()))
                .subscribe(change -> {
                    String inserted = change.getInserted();
                    if (inserted.endsWith(".")) {
                        showAutoComplete(change.getPosition());
                    }
                });

        // if the user clicks outside of the popup, hide it
        setOnMouseClicked(event -> {
            if (autoCompletePopup.get() != null && autoCompletePopup.get().isShowing()) {
                hideAutoComplete();
            }
        });
    }

    private @Nullable Pair<Integer, Integer> getIdentifierRangeBeforeDot(int dotPosition) {
        String text = getText();
        if (dotPosition < 0 || text.charAt(dotPosition) != '.') {
            return null;
        }

        int start = dotPosition;
        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
            start--;
        }

        return new Pair<>(start, dotPosition - start + 1);
    }

    private void showAutoComplete(int position) {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setSource(getText().toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        parser.setUnitName(filePath.getFileName().toString());
        String[] classpathEntries = {"D:/Program Files/Java/temurin-21.0.3/jmods/java.base.jmod"};

        parser.setEnvironment(classpathEntries, null, null, false);

        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);

        Pair<Integer, Integer> range = getIdentifierRangeBeforeDot(position);
        if (range == null) {
            hideAutoComplete();
            return;
        }

        int start = range.getKey();
        int length = range.getValue();

        var finder = new NodeFinder(compilationUnit, start, length);
        ASTNode node = finder.getCoveredNode();
        if (node == null) {
            node = finder.getCoveringNode();
        }

        if (node == null) {
            hideAutoComplete();
            return;
        }

        List<String> suggestions = new ArrayList<>();
        if (node instanceof ExpressionStatement statement) {
            node = statement.getExpression();
        }

        if (node instanceof Expression expr) {
            ITypeBinding binding = expr.resolveTypeBinding();
            if (binding != null) {
                String typeName = binding.getQualifiedName();
                suggestions.addAll(this.autocomplete.suggestMembers(typeName, ""));
            }
        }

        if (suggestions.isEmpty()) {
            hideAutoComplete();
            return;
        }

        this.fullSuggestions.clear();
        this.fullSuggestions.addAll(suggestions);
        this.dotPosition = position;

        Platform.runLater(() -> {
            var popup = new Popup();
            var listView = new RRListView<String>();
            listView.getItems().addAll(suggestions);

            listView.setOnMouseClicked(event -> {
                String selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    int currentCaret = getCaretPosition();
                    int startPos = this.dotPosition + 1; // After the dot
                    replaceText(startPos, currentCaret, selected); // Replace prefix with selection
                    hideAutoComplete();
                }
            });

            popup.getContent().add(listView);
            int caretX = getCaretBounds().map(Bounds::getMaxX).map(Double::intValue).orElse(0);
            int caretY = getCaretBounds().map(Bounds::getMaxY).map(Double::intValue).orElse(0);
            popup.setAutoHide(true);
            this.autoCompletePopup.set(popup);

            textListener = (obs, oldText, newText) -> {
                Popup currentPopup = autoCompletePopup.get();
                if (currentPopup != null && currentPopup.isShowing()) {
                    int currentCaret = getCaretPosition();
                    if (currentCaret > dotPosition && getText().charAt(dotPosition) == '.') {
                        String prefix = "";
                        if (currentCaret > dotPosition + 1) {
                            prefix = getText().substring(dotPosition + 1, currentCaret);
                        }

                        final String finalPrefix = prefix;
                        List<String> filtered = fullSuggestions.stream()
                                .filter(s -> s.startsWith(finalPrefix))
                                .collect(Collectors.toList());
                        listView.getItems().setAll(filtered);
                    } else {
                        hideAutoComplete();
                    }
                }
            };

            textProperty().addListener(textListener);
            popup.show(this, caretX, caretY);
        });
    }

    private void hideAutoComplete() {
        Platform.runLater(() -> {
            Popup currentPopup = autoCompletePopup.get();
            if (currentPopup != null) {
                currentPopup.hide();
                autoCompletePopup.set(null);
            }

            if (textListener != null) {
                textProperty().removeListener(textListener);
                textListener = null;
            }
        });
    }

    private void errorHighlighting() {
        try {
            Task<DiagnosticCollector<JavaFileObject>> task = requestErrorDiagnostics();
            task.run();
            DiagnosticCollector<JavaFileObject> diagnostics = task.get(10, TimeUnit.SECONDS);

            applyErrorHighlighting(diagnostics);
        } catch (InterruptedException | ExecutionException | TimeoutException exception) {
            Railroad.LOGGER.error("Failed to compile", exception);
        }

        plainTextChanges()
                .successionEnds(Duration.ofMillis(500))
                .retainLatestUntilLater(executor0)
                .supplyTask(this::requestErrorDiagnostics)
                .awaitLatest(plainTextChanges())
                .filterMap(throwable -> {
                    if (throwable.isSuccess()) {
                        return throwable.toOptional();
                    } else {
                        Railroad.LOGGER.error("Failed to compile", throwable.getFailure());
                        return Optional.empty();
                    }
                })
                .subscribe(this::applyErrorHighlighting);

        this.errors.addListener((MapChangeListener<Diagnostic<? extends JavaFileObject>, Popup>) change -> {
            if (change.wasRemoved()) {
                change.getValueRemoved().hide();
            }
        });
    }

    private Task<DiagnosticCollector<JavaFileObject>> requestErrorDiagnostics() {
        Task<DiagnosticCollector<JavaFileObject>> task = new Task<>() {
            @Override
            protected DiagnosticCollector<JavaFileObject> call() {
                var source = new JavaSourceFromString(JavaCodeEditorPane.this.filePath.getFileName().toString().replace(".java", ""), getText());
                DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
                JavaCompiler.CompilationTask task = JAVA_COMPILER.getTask(null, null, diagnostics, List.of("-Xlint:all"), null, List.of(source));
                task.call();
                return diagnostics;
            }
        };

        executor0.submit(task);
        return task;
    }

    private void applyErrorHighlighting(DiagnosticCollector<JavaFileObject> diagnostics) {
        long startTime = System.currentTimeMillis();

        // Process diagnostics for both errors and warnings
        Map<Diagnostic<? extends JavaFileObject>, Popup> diagnosticsMap = diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR ||
                        diagnostic.getKind() == Diagnostic.Kind.WARNING ||
                        diagnostic.getKind() == Diagnostic.Kind.MANDATORY_WARNING)
                .sorted(Comparator.comparingLong(Diagnostic::getStartPosition))
                .collect(HashMap::new, (map, diagnostic) -> {
                    int start = (int) diagnostic.getStartPosition();
                    int end = (int) diagnostic.getEndPosition();
                    String message = diagnostic.getMessage(null);
                    String styleClass = diagnostic.getKind() == Diagnostic.Kind.ERROR ? "error" : "warning";
                    setStyleClass(start, end, styleClass);

                    var popup = new Popup();
                    popup.getContent().add(new DiagnosticPane(diagnostic));

                    // Show popup on hover
                    addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN, event -> {
                        int position = event.getCharacterIndex();
                        if (position >= start && position <= end) {
                            Point2D screenPosition = event.getScreenPosition();
                            popup.show(JavaCodeEditorPane.this, screenPosition.getX(), screenPosition.getY());
                        }
                    });

                    // Hide popup when mouse leaves
                    addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
                        if (!popup.isShowing()) return;
                        int charIndex = JavaCodeEditorPane.this.hit(event.getX(), event.getY())
                                .getCharacterIndex().orElse(-1);
                        if (charIndex < start || charIndex > end) {
                            popup.hide();
                        }
                    });

                    map.put(diagnostic, popup);
                    Railroad.LOGGER.error("Issue at L{}:{} - {}", diagnostic.getLineNumber(),
                            diagnostic.getColumnNumber(), message);
                }, HashMap::putAll);

        // Update the errors map
        this.errors.clear();
        this.errors.putAll(diagnosticsMap);

        // Update lineToSeverity map
        lineToSeverity.clear();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            Diagnostic.Kind kind = diagnostic.getKind();
            if (kind == Diagnostic.Kind.ERROR || kind == Diagnostic.Kind.WARNING ||
                    kind == Diagnostic.Kind.MANDATORY_WARNING) {
                int line = (int) diagnostic.getLineNumber();
                if (kind == Diagnostic.Kind.ERROR) {
                    lineToSeverity.put(line, Diagnostic.Kind.ERROR); // Errors take precedence
                } else if (!lineToSeverity.containsKey(line) ||
                        lineToSeverity.get(line) != Diagnostic.Kind.ERROR) {
                    lineToSeverity.put(line, Diagnostic.Kind.WARNING); // Warnings if no error
                }
            }
        }

        // Force redraw of margin graphics
        requestLayout();

        Railroad.LOGGER.debug("Error highlighting took {}ms", System.currentTimeMillis() - startTime);
    }

    private void syntaxHighlight() {
        applyHighlighting(computeHighlighting(getText()));
        multiPlainChanges()
                .successionEnds(Duration.ofMillis(500))
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
        return TreeSitterJavaSyntaxHighlighting.computeHighlighting(text);
    }
}