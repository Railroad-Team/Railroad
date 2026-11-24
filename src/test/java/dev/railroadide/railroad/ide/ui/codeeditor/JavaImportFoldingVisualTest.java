package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import org.kordamp.ikonli.javafx.FontIcon;
import static org.junit.jupiter.api.Assertions.*;

public class JavaImportFoldingVisualTest {
    @TempDir
    public Path directory;

    @BeforeAll
    public static void startJavaFx() throws Exception {
        var started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        onFx(() -> Platform.setImplicitExit(false));
    }

    @Test
    public void foldedImportsRenderInTextAreaAndExpandFromEllipsis() throws Exception {
        Path file = directory.resolve("Example.java");
        String source = "package example;\n\nimport java.util.List;\nimport java.util.Map;\n\nclass Example {}\n";
        Files.writeString(file, source);
        var editorRef = new AtomicReference<TextEditorPane>();
        var stageRef = new AtomicReference<Stage>();
        var highlightBackground = new AtomicReference<Background>();
        try {
            onFx(() -> {
                var editor = new TextEditorPane(DocumentId.create(), file, "java");
                editorRef.set(editor);
                var root = new StackPane(editor);
                var scene = new Scene(root, 700, 350);
                scene.getStylesheets()
                    .add(getClass().getResource("/assets/railroad/styles/default-dark.css").toExternalForm());
                scene.getStylesheets().add(getClass().getResource("/assets/railroad/styles/base.css").toExternalForm());
                scene.getStylesheets()
                    .add(getClass().getResource("/assets/railroad/styles/components/code-area.css").toExternalForm());
                var stage = new Stage();
                stageRef.set(stage);
                stage.setScene(scene);
                stage.show();
                stage.toFront();
                stage.requestFocus();
                root.applyCss();
                root.layout();
            });
            Thread.sleep(500);
            onFx(() -> {
                var marker = editorRef.get().lookup(".editor-fold-marker");
                assertNotNull(marker);
                var icon = (FontIcon) marker.lookup(".editor-fold-chevron");
                assertEquals("Font Awesome 6 Free Solid", icon.getFont().getFamily());
                click(marker);
            });
            Thread.sleep(700);
            onFx(() -> {
                var editor = editorRef.get();
                assertTrue(editor.isFolded(3));
                assertFalse(editor.getParagraphGraphicFactory().apply(3).isVisible());
                var placeholder = editor.lookup(".editor-fold-placeholder");
                var dots = editor.lookup(".editor-fold-placeholder-action");
                var marker = editor.lookup(".editor-fold-marker");
                assertNotNull(placeholder);
                assertTrue(placeholder.isVisible());
                assertNotNull(dots);
                var placeholderBounds = placeholder.localToScreen(placeholder.getBoundsInLocal());
                var markerBounds = marker.localToScreen(marker.getBoundsInLocal());
                var textBounds = editor
                    .getCharacterBoundsOnScreen(editor.getAbsolutePosition(2, 0), editor.getAbsolutePosition(2, 1))
                    .orElseThrow();
                assertTrue(markerBounds.getMaxX() < placeholderBounds.getMinX());
                assertEquals(textBounds.getMinX(), placeholderBounds.getMinX(), 2);
                var paragraphBounds = editor.getParagraphBoundsOnScreen(2).orElseThrow();
                assertEquals(paragraphBounds.getMinY(), placeholderBounds.getMinY(), 0.5);
                assertEquals(paragraphBounds.getHeight(), placeholderBounds.getHeight(), 0.5);
                assertEquals(source, editor.getText());
                var fullLine = editor.getCharacterBoundsOnScreen(editor.getAbsolutePosition(2, 0),
                    editor.getAbsolutePosition(2, editor.getParagraph(2).length())).orElseThrow();
                assertTrue(placeholderBounds.getWidth() >= fullLine.getWidth());
                var keyword = (Label) editor.lookup(".editor-fold-keyword");
                assertNotNull(keyword);
                assertNotEquals(((Label) dots).getTextFill(), keyword.getTextFill());
                editor.moveTo(2, 0);
            });
            Thread.sleep(300);
            onFx(() -> {
                var editor = editorRef.get();
                var placeholder = (StackPane) editor.lookup(".editor-fold-placeholder");
                assertTrue(placeholder.getPseudoClassStates().contains(PseudoClass.getPseudoClass("has-caret")));
                placeholder.applyCss();
                highlightBackground.set(placeholder.getBackground());
                editor.moveTo(0, 0);
            });
            Thread.sleep(300);
            onFx(() -> {
                var editor = editorRef.get();
                var placeholder = (StackPane) editor.lookup(".editor-fold-placeholder");
                assertFalse(placeholder.getPseudoClassStates().contains(PseudoClass.getPseudoClass("has-caret")));
                placeholder.applyCss();
                assertNotEquals(highlightBackground.get(), placeholder.getBackground());
                var dots = editor.lookup(".editor-fold-placeholder-action");
                click(dots);
            });
            Thread.sleep(500);
            onFx(() -> {
                assertFalse(editorRef.get().isFolded(3));
                assertNull(editorRef.get().lookup(".editor-fold-placeholder"));
                assertEquals(source, editorRef.get().getText());
            });
        } finally {
            onFx(() -> {
                if (editorRef.get() != null) {
                    editorRef.get().discardChangesOnClose();
                    editorRef.get().close();
                    editorRef.get().dispose();
                }
                if (stageRef.get() != null) {
                    stageRef.get().close();
                }
            });
        }
    }

    private static void click(Node node) {
        Event.fireEvent(node, new MouseEvent(
            MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, MouseButton.PRIMARY, 1,
            false, false, false, false, false, false, false, false, false, true, null));
    }

    private static void onFx(Runnable action) throws Exception {
        var task = new FutureTask<Void>(() -> {
            action.run();
            return null;
        });
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
