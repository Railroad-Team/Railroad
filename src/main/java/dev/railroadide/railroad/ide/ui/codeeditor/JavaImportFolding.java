package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.ide.sst.impl.java.JavaLexer;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.lexer.Token;
import dev.railroadide.railroad.ide.sst.lexer.TokenChannel;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import java.util.Objects;

public final class JavaImportFolding {
    private static final PseudoClass HAS_CARET = PseudoClass.getPseudoClass("has-caret");
    private final TextEditorPane editor;
    private Range range;
    private StackPane placeholder;
    private boolean closed;
    private final AnimationTimer placeholderTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            positionPlaceholder();
        }
    };

    public JavaImportFolding(TextEditorPane editor) {
        this.editor = editor;
        update();
        editor.getGutter().addTrailingColumn(new GutterColumn("java-import-fold", 16, line -> {
            if (range == null || line != range.start() + 1)
                return null;
            boolean folded = editor.isFolded(range.start() + 1);
            var chevron = new FontIcon(folded ? FontAwesomeSolid.CHEVRON_RIGHT : FontAwesomeSolid.CHEVRON_DOWN);
            chevron.setStyle("-fx-icon-size: 10px; -fx-font-family: 'Font Awesome 6 Free Solid';");
            chevron.getStyleClass().add("editor-fold-chevron");
            var marker = new StackPane(chevron);
            marker.getStyleClass().add("editor-fold-marker");
            marker.setMinSize(16, 16);
            marker.setPrefSize(16, 16);
            marker.setMaxSize(16, 16);
            marker.setCursor(Cursor.HAND);
            if (folded) {
                Platform.runLater(this::showPlaceholder);
            }
            return marker;
        }, (line, event) -> {
            if (event.getButton() != MouseButton.PRIMARY || range == null || line != range.start() + 1)
                return;
            toggle();
            event.consume();
        }));
    }

    private void toggle() {
        if (range == null)
            return;
        if (editor.isFolded(range.start() + 1)) {
            editor.unfoldParagraphs(range.start());
        } else {
            editor.foldParagraphs(range.start(), range.end());
        }
        if (editor.isFolded(range.start() + 1)) {
            showPlaceholder();
        } else {
            hidePlaceholder();
        }
        editor.getGutter().refresh();
    }

    private void showPlaceholder() {
        if (closed || placeholder != null || range == null || range.start() + 1 >= editor.getParagraphs().size()
            || !editor.isFolded(range.start() + 1))
            return;
        String firstLine = editor.getParagraph(range.start()).getText();
        int importStart = firstLine.indexOf("import");
        var indent = new Label(firstLine.substring(0, Math.max(0, importStart)));
        var keyword = new Label("import");
        keyword.getStyleClass().add("editor-fold-keyword");
        keyword.setStyle("-fx-text-fill: -syntax-keyword;");
        var original = new Text(firstLine);
        original.setOpacity(0);
        original.fontProperty().bind(keyword.fontProperty());
        var dots = new Label("...");
        dots.getStyleClass().add("editor-fold-placeholder-action");
        dots.setCursor(Cursor.HAND);
        dots.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                toggle();
                event.consume();
            }
        });
        var replacement = new HBox(indent, keyword, new Label(" "), dots, new Label(";"));
        replacement.setAlignment(Pos.CENTER_LEFT);
        placeholder = new StackPane(original, replacement);
        placeholder.setAlignment(Pos.CENTER_LEFT);
        placeholder.getStyleClass().add("editor-fold-placeholder");
        placeholder.setManaged(false);
        placeholder.setVisible(false);
        editor.addFoldOverlay(placeholder);
        placeholderTimer.start();
    }

    private void positionPlaceholder() {
        if (placeholder == null || range == null || range.start() + 1 >= editor.getParagraphs().size()
            || !editor.isFolded(range.start() + 1)) {
            hidePlaceholder();
            editor.getGutter().refresh();
            return;
        }
        int start = editor.getAbsolutePosition(range.start(), 0);
        int length = editor.getParagraph(range.start()).length();
        var bounds = editor.getCharacterBoundsOnScreen(start, start + length);
        var paragraphBounds = editor.getParagraphBoundsOnScreen(range.start());
        if (bounds.isEmpty() || paragraphBounds.isEmpty() || editor.getScene() == null) {
            placeholder.setVisible(false);
            return;
        }
        var local = editor.screenToLocal(bounds.get());
        var paragraphLocal = editor.screenToLocal(paragraphBounds.get());
        placeholder.pseudoClassStateChanged(HAS_CARET, editor.getCurrentParagraph() == range.start());
        placeholder.applyCss();
        placeholder.autosize();
        placeholder.resize(Math.max(placeholder.prefWidth(-1), local.getWidth()), paragraphLocal.getHeight());
        placeholder.relocate(local.getMinX(), paragraphLocal.getMinY());
        placeholder.toFront();
        placeholder.setVisible(true);
    }

    private void hidePlaceholder() {
        placeholderTimer.stop();
        if (placeholder != null) {
            editor.removeFoldOverlay(placeholder);
            placeholder = null;
        }
    }

    public void close() {
        closed = true;
        hidePlaceholder();
    }

    public void update() {
        Range next = findRange(editor.getText());
        if (!Objects.equals(range, next)) {
            hidePlaceholder();
            range = next;
            editor.getGutter().refresh();
        }
    }

    public static Range findRange(String text) {
        var lexer = new JavaLexer(text);
        int start = -1;
        int end = -1;
        boolean inImport = false;
        boolean inPackage = false;
        while (true) {
            Token<JavaTokenType> token = lexer.nextToken();
            JavaTokenType type = token.type();
            if (type == JavaTokenType.EOF)
                break;
            if (token.channel() != TokenChannel.DEFAULT || type == null)
                continue;
            if (inImport) {
                if (type == JavaTokenType.SEMICOLON) {
                    end = paragraphAt(text, token.pos());
                    inImport = false;
                }
            } else if (inPackage) {
                if (type == JavaTokenType.SEMICOLON) {
                    inPackage = false;
                }
            } else if (type == JavaTokenType.PACKAGE_KEYWORD && start < 0) {
                inPackage = true;
            } else if (type == JavaTokenType.IMPORT_KEYWORD) {
                if (start < 0) {
                    start = paragraphAt(text, token.pos());
                }
                inImport = true;
            } else
                break;
        }
        return start >= 0 && end > start ? new Range(start, end) : null;
    }

    private static int paragraphAt(String text, int offset) {
        int paragraph = 0;
        for (int index = 0; index < offset; index++) {
            if (text.charAt(index) == '\n') {
                paragraph++;
            }
        }
        return paragraph;
    }

    public record Range(int start, int end) {
    }
}
