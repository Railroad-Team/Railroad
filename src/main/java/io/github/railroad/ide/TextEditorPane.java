package io.github.railroad.ide;

import io.github.railroad.Railroad;
import io.github.railroad.utility.ShutdownHooks;
import javafx.scene.input.KeyCode;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TextEditorPane extends CodeArea {
    protected final Path filePath;

    private static final int[] FONT_SIZES = {6, 8, 10, 12, 14, 16, 18, 20, 24, 26, 28, 30, 36, 40, 48, 56, 60};
    private int fontSizeIndex = 5;

    public TextEditorPane(Path item) {
        this.filePath = item;

        setParagraphGraphicFactory(LineNumberFactory.get(this));
        setMouseOverTextDelay(Duration.ofMillis(500));

        listenForChanges();
        resizableFont();

        moveTo(0);
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
}
