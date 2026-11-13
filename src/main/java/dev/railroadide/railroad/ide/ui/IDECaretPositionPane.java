package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.plugin.spi.editor.EditorSubscription;
import dev.railroadide.railroad.plugin.spi.editor.TextEditorAccess;
import dev.railroadide.railroad.settings.CaretPositionFormat;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.localized.LocalizedText;

import java.util.function.BiConsumer;

/** Displays the caret position of the active managed document, including detached editors. */
public class IDECaretPositionPane extends RRHBox {
    private final LocalizedText text;
    private TextEditorAccess observedEditor;
    private EditorSubscription activeSubscription;
    private EditorSubscription selectionSubscription;
    private EditorSubscription documentSubscription;

    /** Creates the active editor's one-based line and column display. */
    public IDECaretPositionPane() {
        text = new LocalizedText("");
        text.getStyleClass().add("column-number");
        getChildren().add(text);

        BiConsumer<CaretPositionFormat, CaretPositionFormat> settingListener = (_, _) -> update();
        sceneProperty().addListener((_, _, scene) -> {
            detach();
            Settings.CARET_POSITION_FORMAT.removeListener(settingListener);
            if (scene != null) {
                Settings.CARET_POSITION_FORMAT.addListener(settingListener);
                activeSubscription = Services.DOCUMENT_EDITOR_STATE.observeActiveTextEditor(
                    editor -> observe(editor.orElse(null)));
            }
        });
    }

    private void detach() {
        if (activeSubscription != null) {
            activeSubscription.close();
            activeSubscription = null;
        }
        observe(null);
    }

    private void observe(TextEditorAccess editor) {
        if (selectionSubscription != null) {
            selectionSubscription.close();
            selectionSubscription = null;
        }
        if (documentSubscription != null) {
            documentSubscription.close();
            documentSubscription = null;
        }
        observedEditor = editor;
        if (editor != null) {
            selectionSubscription = editor.observeSelection(_ -> update());
            documentSubscription = editor.observe(_ -> update());
        }
        update();
    }

    private void update() {
        if (observedEditor == null) {
            text.setText("");
            return;
        }

        try {
            var position = observedEditor.snapshot().lineMap().positionAt(observedEditor.selection().caret());
            text.setKeyAndArgs(Settings.CARET_POSITION_FORMAT.getValue().getIdeTranslationKey(),
                position.line() + 1, position.column() + 1);
        } catch (IllegalStateException exception) {
            text.setText("");
        }
    }
}
