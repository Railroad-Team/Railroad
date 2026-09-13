package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.settings.CaretPositionFormat;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.id.UIIds;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;

import java.util.Objects;

/**
 * Provides the registered status-bar container for the active IDE workspace.
 */
public class IDEStatusBarPane extends RRHBox {
    /**
     * Creates a status bar registered while attached to a scene.
     */
    private final Text text;
    private final ChangeListener<Number> caretListener = (_, _, _) -> update();
    private TextEditorPane observedEditor;
    private boolean editorListenerInstalled;

    public IDEStatusBarPane() {
        text = new Text("1:1");
        text.getStyleClass().add("column-number");

        setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(this, Priority.ALWAYS);
        getChildren().add(text);

        sceneProperty().addListener((_, _, scene) -> {
            if (scene != null && !editorListenerInstalled)
                Platform.runLater(this::installEditorListener);
        });

        Services.UI_MANAGER.assignWhileAttached(UIIds.IDE.IDE_STATUS_BAR, this);
    }

    private void installEditorListener() {
        Services.UI_MANAGER.lookup(UIIds.IDE.IDE_CODE_EDITOR_DOCK).ifPresent(tabPane -> {
            editorListenerInstalled = true;

            var selectedContent = tabPane.getSelectionModel()
                .selectedItemProperty()
                .flatMap(Tab::contentProperty);

            selectedContent.addListener((_, _, content) -> observe(content));
            observe(selectedContent.getValue());
        });
    }

    private void observe(Node content) {
        if (observedEditor != null) {
            observedEditor.caretPositionProperty().removeListener(caretListener);
        }

        if(content instanceof TextEditorPane editor){
            observedEditor = editor;
            observedEditor.caretPositionProperty().addListener(caretListener);
            update();
        } else if(content instanceof MarkdownPreviewPane markdownPane) {
            markdownPane.editorProperty().addListener(
                (_, _, newEditor) -> observe(newEditor)
            );

            observe(markdownPane.getMarkdownEditorPane());
        } else {
            text.setText("");
        }
    }

    private void update() {
        if (observedEditor == null)
            return;

        int lineNumber = observedEditor.getCurrentParagraph() + 1;
        int column = observedEditor.getCaretColumn() + 1;
        text.setText(Objects.equals(Settings.CARET_POSITION_FORMAT.getValue(), CaretPositionFormat.COLON) ?
            lineNumber + ":" + column : "ln " + lineNumber + ", col " + column);
    }
}
