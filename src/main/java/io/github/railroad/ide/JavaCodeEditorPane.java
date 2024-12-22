package io.github.railroad.ide;

import io.github.railroad.Railroad;
import io.github.railroad.ide.indexing.Indexes;
import io.github.railroad.ide.indexing.Trie;
import io.github.railroad.ide.syntaxhighlighting.TreeSitterJavaSyntaxHighlighting;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRListView;
import io.github.railroad.utility.ShutdownHooks;
import io.github.railroad.utility.compiler.JavaSourceFromString;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.concurrent.Task;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Popup;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxmisc.richtext.model.StyleSpans;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import javax.tools.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;

public class JavaCodeEditorPane extends TextEditorPane {
    private static final JavaCompiler JAVA_COMPILER = ToolProvider.getSystemJavaCompiler();

    private final ExecutorService executor0 = Executors.newFixedThreadPool(2);
    private final ObservableMap<Diagnostic<? extends JavaFileObject>, Popup> errors = FXCollections.observableHashMap();

    public JavaCodeEditorPane(Path item) {
        super(item);

        // marginErrors();

        syntaxHighlight();
        errorHighlighting();
        // codeCompletion();

        ShutdownHooks.addHook(executor0::shutdown);
    }

    private void marginErrors() {
        IntFunction<? extends Node> lineNumberFactory = LineNumberFactory.get(this);
        setParagraphGraphicFactory(value -> {
            var hbox = new RRHBox();
            hbox.setMinWidth(50);
            Node node = lineNumberFactory.apply(value);
            if(node instanceof Label label) {
                label.setTextAlignment(TextAlignment.LEFT);
            }

            hbox.getChildren().add(node);

            int line = value + 1;
            List<Diagnostic<? extends JavaFileObject>> diagnostics = errors.keySet().stream()
                    .filter(diagnostic -> diagnostic.getLineNumber() == line)
                    .toList();

            if (diagnostics.isEmpty()) {
                hbox.setPadding(new Insets(0, 5, 0, 0));
                hbox.setMinWidth(Region.USE_PREF_SIZE);
                return hbox;
            }

            var icon = new FontIcon(FontAwesomeSolid.EXCLAMATION_TRIANGLE);
            icon.setIconColor(Color.RED);
            var popup = new Popup();
            popup.getContent().add(diagnostics.size() == 1 ? new DiagnosticPane(diagnostics.getFirst()) : new DiagnosticPane(diagnostics));

            icon.setOnMouseEntered(event -> {
                Point2D screenPosition = icon.localToScreen(0, 0);
                popup.show(icon, screenPosition.getX(), screenPosition.getY());
            });

            icon.setOnMouseExited(event -> popup.hide());

            hbox.getChildren().addAll(icon);
            return hbox;
        });
    }

    private void codeCompletion() {
        final Trie trie = Indexes.createTrie();
        plainTextChanges()
                .successionEnds(Duration.ofMillis(500))
                .retainLatestUntilLater(executor0)
                .filter(change -> !change.getInserted().equals(change.getRemoved()))
                .subscribe(change -> {
                    String inserted = change.getInserted();
                    if(inserted.endsWith(".")) {
                        showAutoComplete(trie, change.getPosition());
                    } else if(inserted.equals(" ")) {
                        hideAutoComplete();
                    }
                });
    }

    private void showAutoComplete(Trie trie, int position) {
        String text = getText();
        int start = position;
        while(start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
            start--;
        }

        String prefix = text.substring(start, position);
        List<String> suggestions = trie.searchPrefix(prefix);
        if(suggestions.isEmpty()) {
            hideAutoComplete();
            return;
        }

        var popup = new Popup();
        var listView = new RRListView<String>();
        listView.getItems().addAll(suggestions);

        final int finalStart = start;
        listView.setOnMouseClicked(event -> {
            String selected = listView.getSelectionModel().getSelectedItem();
            replaceText(finalStart, position, selected);
            hideAutoComplete();
        });

        popup.getContent().add(listView);

        int caretX = getCaretBounds().map(Bounds::getMaxX).map(Double::intValue).orElse(0);
        int caretY = getCaretBounds().map(Bounds::getMaxY).map(Double::intValue).orElse(0);

        popup.show(this, caretX, caretY);
    }

    private void hideAutoComplete() {

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

        Map<Diagnostic<? extends JavaFileObject>, Popup> errors = diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .sorted(Comparator.comparingLong(Diagnostic::getStartPosition))
                .collect(HashMap::new, (map, diagnostic) -> {
                    int start = (int) diagnostic.getStartPosition();
                    int end = (int) diagnostic.getEndPosition();
                    String message = diagnostic.getMessage(null);

                    var popup = new Popup();
                    popup.getContent().add(new DiagnosticPane(diagnostic));

                    // show the popup if the mouse hovers over the error
                    addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN, event -> {
                        int position = event.getCharacterIndex();
                        if (position >= start && position <= end) {
                            Point2D screenPosition = event.getScreenPosition();
                            popup.show(JavaCodeEditorPane.this, screenPosition.getX(), screenPosition.getY());
                        }
                    });

                    // hide the popup if the mouse leaves the error
                    addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
                        if (!popup.isShowing())
                            return;

                        int charIndex = JavaCodeEditorPane.this.hit(event.getX(), event.getY()).getCharacterIndex().orElse(-1);
                        if (charIndex < start || charIndex > end) {
                            popup.hide();
                        }
                    });

                    map.put(diagnostic, popup);
                    setStyleClass(start, end, "error");

                    Railroad.LOGGER.error("Error at L{}:{} - {}", diagnostic.getLineNumber(), diagnostic.getColumnNumber(), message);
                }, HashMap::putAll);

        this.errors.clear();
        this.errors.putAll(errors);

        Railroad.LOGGER.info("Error highlighting took {}ms", System.currentTimeMillis() - startTime);
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