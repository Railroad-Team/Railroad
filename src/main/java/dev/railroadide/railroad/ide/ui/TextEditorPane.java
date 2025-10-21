package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.plugin.defaults.DefaultDocument;
import dev.railroadide.railroad.utility.ShutdownHooks;
import dev.railroadide.railroadpluginapi.events.FileEvent;
import dev.railroadide.railroadpluginapi.events.FileModifiedEvent;
import javafx.scene.input.KeyCode;
import javafx.util.Pair;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.PlainTextChange;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TextEditorPane extends CodeArea {
    private static final int[] FONT_SIZES = {6, 8, 10, 12, 14, 16, 18, 20, 24, 26, 28, 30, 36, 40, 48, 56, 60};
    protected final Path filePath;
    private final ExecutorService changeExecutor = Executors.newSingleThreadExecutor();
    private int fontSizeIndex = 5;

    public TextEditorPane(Path item) {
        this.filePath = item;

        setParagraphGraphicFactory(LineNumberFactory.get(this));
        setMouseOverTextDelay(Duration.ofMillis(500));

        listenForChanges();
        resizableFont();

        moveTo(0);
    }

    private static FileModifiedEvent.Change getChange(String text, PlainTextChange change) {
        String inserted = change.getInserted();
        String removed = change.getRemoved();
        int position = change.getPosition();
        int netLength = change.getNetLength();

        Pair<Integer, Integer> startPos = getLineAndColumn(text, position);
        Pair<Integer, Integer> endPos = getLineAndColumn(text, position + netLength);

        return new FileModifiedEvent.Change(
            getChangeType(change),
            removed,
            inserted,
            new FileModifiedEvent.Range(
                startPos.getKey(), startPos.getValue(),
                endPos.getKey(), endPos.getValue()));
    }

    private static FileModifiedEvent.Change.Type getChangeType(PlainTextChange change) {
        String inserted = change.getInserted();
        String removed = change.getRemoved();

        if (!inserted.isEmpty() && removed.isEmpty()) {
            return FileModifiedEvent.Change.Type.ADDED;
        } else if (inserted.isEmpty() && !removed.isEmpty()) {
            return FileModifiedEvent.Change.Type.REMOVED;
        } else {
            return FileModifiedEvent.Change.Type.MODIFIED;
        }
    }

    private static Pair<Integer, Integer> getLineAndColumn(String text, int position) {
        int line = 0;
        int column = 0;

        for (int i = 0; i < position && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }

        return new Pair<>(line, column);
    }

    private void resizableFont() {
        updateFontSizeClass();

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
            updateFontSizeClass();
        });
    }

    private void updateFontSizeClass() {
        getStyleClass().removeIf(styleClass -> styleClass.startsWith("text-editor-font-size-"));
        getStyleClass().add("text-editor-font-size-" + FONT_SIZES[fontSizeIndex]);
    }

    private void listenForChanges() {
        try {
            replaceText(0, 0, Files.readString(this.filePath));

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
                                    String text = getText();
                                    if (!text.equals(content)) {
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

            multiPlainChanges()
                .successionEnds(Duration.ofMillis(500))
                .retainLatestUntilLater(changeExecutor)
                .map(changes -> changes.stream()
                    .map(change -> TextEditorPane.getChange(getText(), change))
                    .toList())
                .subscribe(changes -> {
                    var document = new DefaultDocument(this.filePath.getFileName().toString(), this.filePath);
                    Railroad.EVENT_BUS.publish(new FileModifiedEvent(document, changes));

                    String text = getText();
                    try {
                        if (!Files.readString(this.filePath).equals(text)) {
                            Files.writeString(this.filePath, text);
                            Railroad.EVENT_BUS.publish(new FileEvent(
                                document,
                                FileEvent.EventType.SAVED));
                        }
                    } catch (IOException exception) {
                        Railroad.LOGGER.error("Failed to write file", exception);
                    }
                });
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to read file", exception);
        }
    }
}
