package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.ui.TextEditorPane;
import javafx.scene.Node;
import org.jspecify.annotations.Nullable;

public record EditorOpenView(
    Node content,
    @Nullable TextEditorPane activeEditor,
    String languageId
) {
}
