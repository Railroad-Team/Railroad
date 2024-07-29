package io.github.railroad.ide.syntax_tests;

import io.github.railroad.Railroad;
import io.github.railroad.utility.ShutdownHooks;
import javafx.concurrent.Task;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTreeCursor;
import org.treesitter.TreeSitterJava;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TreeSitterJavaEditorPane extends CodeArea {
    private final ExecutorService executor0 = Executors.newSingleThreadExecutor();

    public TreeSitterJavaEditorPane(Path item) {
        setParagraphGraphicFactory(LineNumberFactory.get(this));
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

        try {
            replaceText(0, 0, Files.readString(item));

            // listen for changes to the file
            try (var watcher = item.getFileSystem().newWatchService()) {
                item.getParent().register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);

                ExecutorService executor1 = Executors.newSingleThreadExecutor();
                executor1.submit(() -> {
                    while (!Thread.interrupted()) {
                        try {
                            var key = watcher.take();
                            for (var event : key.pollEvents()) {
                                if (event.context().equals(item.getFileName())) {
                                    String content = Files.readString(item);
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
                    if (!Files.readString(item).equals(newText)) {
                        Files.writeString(item, newText);
                    }
                } catch (IOException exception) {
                    Railroad.LOGGER.error("Failed to write file", exception);
                }
            });
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to read file", exception);
        }

        ShutdownHooks.addHook(executor0::shutdown);
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

    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        long start = System.currentTimeMillis();

        var highlighter = new SyntaxHighlighter(text);
        highlighter.traverseTree(null, highlighter.rootNode);

        var styles = highlighter.spansBuilder.create();
        Railroad.LOGGER.info("Computed highlighting in {} ms", System.currentTimeMillis() - start);
        return styles;
    }

    private static class SyntaxHighlighter {
        private final StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        private final String text;
        private final TSNode rootNode;
        private int currentPosition;

        public SyntaxHighlighter(String text) {
            this.text = text;

            var parser = new TSParser();
            parser.setLanguage(new TreeSitterJava());

            var tree = parser.parseString(null, text);
            this.rootNode = tree.getRootNode();
        }

        public void traverseTree(TSTreeCursor cursor, TSNode node) {
            cursor = cursor == null ? new TSTreeCursor(node) : cursor;

            do {
                TSNode currentNode = cursor.currentNode();
                String type = currentNode.getType();
                int start = currentNode.getStartByte();
                int end = currentNode.getEndByte();

                if (this.currentPosition < start) {
                    spansBuilder.add(Collections.emptyList(), start - this.currentPosition);
                }

                if(!cursor.gotoFirstChild()) {
                    System.out.println(type + ": " + text.substring(start, end));

                    switch (type) {
                        case "line_comment" -> spansBuilder.add(Collections.singleton("comment"), end - start);
                        case "string_literal" -> spansBuilder.add(Collections.singleton("string"), end - start);
                        case "decimal_integer_literal" ->
                                spansBuilder.add(Collections.singleton("number"), end - start);
                        case "identifier" -> spansBuilder.add(Collections.singleton("name"), end - start);
                        case "package" -> spansBuilder.add(Collections.singleton("package"), end - start);
                        case "modifiers" -> spansBuilder.add(Collections.singleton("modifier"), end - start);
                        case "import" -> spansBuilder.add(Collections.singleton("import"), end - start);
                        default -> spansBuilder.add(Collections.emptyList(), end - start);
                    }
                } else {
                    cursor.gotoParent();
                }

                this.currentPosition = end;

                // Recursively traverse children
                if (cursor.gotoFirstChild()) {
                    traverseTree(cursor, currentNode);
                    cursor.gotoParent();
                }
            } while (cursor.gotoNextSibling());
        }
    }
}