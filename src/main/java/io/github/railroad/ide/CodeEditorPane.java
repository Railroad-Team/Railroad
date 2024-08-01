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
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Popup;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxmisc.richtext.model.StyleSpans;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import javax.tools.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntFunction;

public class CodeEditorPane extends CodeArea {
    private static final JavaCompiler JAVA_COMPILER = ToolProvider.getSystemJavaCompiler();

    private final Path filePath;
    private final ExecutorService executor0 = Executors.newFixedThreadPool(2);
    private final ObservableMap<Diagnostic<? extends JavaFileObject>, Popup> errors = FXCollections.observableHashMap();

    private static final int[] FONT_SIZES = {6, 8, 10, 12, 14, 16, 18, 20, 24, 26, 28, 30, 36, 40, 48, 56, 60};
    private int fontSizeIndex = 5;

    public CodeEditorPane(Path item) {
        this.filePath = item;

        setParagraphGraphicFactory(LineNumberFactory.get(this)); // marginErrors();
        setMouseOverTextDelay(Duration.ofMillis(500));

        syntaxHighlight();
        errorHighlighting();
        // codeCompletion();
        listenForChanges();
        resizableFont();

        moveTo(0);
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

    private void resizableFont() {
        setStyle("-fx-font-size: " + FONT_SIZES[fontSizeIndex] + "px;");

        setOnKeyPressed(event -> {
            if (!event.isControlDown())
                return;

            KeyCode code = event.getCode();
            if (code != KeyCode.EQUALS && code != KeyCode.MINUS)
                return;

            int index = fontSizeIndex + (code == KeyCode.EQUALS ? 1 : -1);
            if (index < 0 || index >= FONT_SIZES.length)
                return;

            fontSizeIndex = index;
            setStyle("-fx-font-size: " + FONT_SIZES[fontSizeIndex] + "px;");
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
                JavaSourceFromString source = new JavaSourceFromString(CodeEditorPane.this.filePath.getFileName().toString().replace(".java", ""), getText());
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
                            popup.show(CodeEditorPane.this, screenPosition.getX(), screenPosition.getY());
                        }
                    });

                    // hide the popup if the mouse leaves the error
                    addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
                        if (!popup.isShowing())
                            return;

                        int charIndex = CodeEditorPane.this.hit(event.getX(), event.getY()).getCharacterIndex().orElse(-1);
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
        ShutdownHooks.addHook(executor0::shutdown);
    }

    private void listenForChanges() {
        try {
            replaceText(0, 0, Files.readString(this.filePath));

            // listen for changes to the file
            try (var watcher = this.filePath.getFileSystem().newWatchService()) {
                this.filePath.getParent().register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);

                ExecutorService executor1 = Executors.newSingleThreadExecutor();
                executor1.submit(() -> {
                    while (!Thread.interrupted()) {
                        try {
                            var key = watcher.take();
                            for (var event : key.pollEvents()) {
                                if (event.context().equals(this.filePath.getFileName())) {
                                    String content = Files.readString(this.filePath);
                                    if (!getText().equals(content)) {
                                        replaceText(0, 0, content);
                                    }
                                }
                            }
                            key.reset();
                        } catch (InterruptedException exception) {
                            Railroad.LOGGER.error("File watcher interrupted", exception);
                        } catch (IOException exception) {
                            Railroad.LOGGER.error("Failed to watch file", exception);
                        }
                    }
                });

                ShutdownHooks.addHook(executor1::shutdown);
            } catch (IOException exception) {
                Railroad.LOGGER.error("Failed to watch file", exception);
            }

            textProperty().addListener((observable, oldText, newText) -> {
                try {
                    if (!Files.readString(this.filePath).equals(newText)) {
                        Files.writeString(this.filePath, newText);
                    }
                } catch (IOException exception) {
                    Railroad.LOGGER.error("Failed to write file", exception);
                }
            });
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to read file", exception);
        }
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