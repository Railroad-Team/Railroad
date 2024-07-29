package io.github.railroad.ide;

import io.github.railroad.Railroad;
import io.github.railroad.ide.syntax_tests.TreeSitterJavaEditorPane;
import io.github.railroad.utility.ShutdownHooks;
import io.github.railroad.utility.compiler.JavaSourceFromString;
import javafx.concurrent.Task;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.Paragraph;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import javax.tools.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CodeEditorPane extends CodeArea {
    private final Path filePath;
    private final ExecutorService executor0 = Executors.newFixedThreadPool(2);
    private static final JavaCompiler JAVA_COMPILER = ToolProvider.getSystemJavaCompiler();

    public CodeEditorPane(Path item) {
        this.filePath = item;

        setParagraphGraphicFactory(LineNumberFactory.get(this));
        syntaxHighlight();
        errorHighlighting();
        codeCompletion();
        listenForChanges();
    }

    private void codeCompletion() {
//        plainTextChanges()
//                .successionEnds(Duration.ofMillis(500))
//                .retainLatestUntilLater(executor0)
//                .filter(change -> !change.getInserted().equals(change.getRemoved()))
//                .subscribe(change -> {
//                    String inserted = change.getInserted();
//                    if(inserted.endsWith(".")) {
//                        showAutoComplete(change.getPosition());
//                    } else if(inserted.equals(" ")) {
//                        hideAutoComplete();
//                    }
//                });
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
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        record StyleRange(int start, int end, String style) {}

        List<StyleRange> styleRanges = new ArrayList<>(diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .map(diagnostic -> new StyleRange((int) diagnostic.getStartPosition(), (int) diagnostic.getEndPosition() + 1, "error"))
                .sorted(Comparator.comparingInt(range -> range.start))
                .toList());

        for (StyleRange styleRange : styleRanges) {
            setStyleClass(styleRange.start, styleRange.end, styleRange.style);
        }

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
        return TreeSitterJavaEditorPane.computeHighlighting(text);
    }
}