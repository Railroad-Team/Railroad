package dev.railroadide.railroad.ide.ui.refactor;

import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.TextRange;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEditPreview;
import dev.railroadide.railroad.localization.L18n;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Shows a {@link WorkspaceEditPreview} as a tree: one entry per changed document or renamed file, each followed by
 * its changed lines before and after the edit. Call on the JavaFX application thread.
 */
public final class WorkspaceEditPreviewView extends TreeView<WorkspaceEditPreviewView.Item> {
    /**
     * Creates an empty preview.
     */
    public WorkspaceEditPreviewView() {
        super(new TreeItem<>());
        setShowRoot(false);
        setCellFactory(_ -> new ItemCell());
        getStyleClass().add("workspace-edit-preview");
    }

    /**
     * Replaces the shown preview.
     *
     * @param preview preview computed by the workspace-edit service
     * @return number of text changes shown
     */
    public int show(WorkspaceEditPreview preview) {
        clear();
        int count = 0;
        for (WorkspaceEditPreview.DocumentPreview document : preview.documents()) {
            var file = new TreeItem<>(new Item(L18n.localize("railroad.rename.preview.file",
                fileName(document.uri()), document.changes().size(),
                L18n.localize("railroad.rename.target." + document.target().name().toLowerCase(Locale.ROOT))),
                true));
            file.setExpanded(true);
            for (WorkspaceEditPreview.ChangePreview change : document.changes()) {
                file.getChildren().add(new TreeItem<>(new Item(describe(document, change), false)));
                count++;
            }

            getRoot().getChildren().add(file);
        }

        for (WorkspaceEditPreview.ResourcePreview resource : preview.resources()) {
            String text = switch (resource.kind()) {
                case RENAME -> L18n.localize("railroad.rename.preview.rename_file", fileName(resource.source()),
                    fileName(resource.target()));
                case CREATE -> L18n.localize("railroad.preview.create_file", fileName(resource.target()));
                case DELETE -> L18n.localize("railroad.preview.delete_file", fileName(resource.source()));
            };

            getRoot().getChildren().add(new TreeItem<>(new Item(text, true)));
        }

        return count;
    }

    /**
     * Removes the shown preview.
     */
    public void clear() {
        getRoot().getChildren().clear();
    }

    /**
     * Returns the shown lines: one per changed file or file operation, followed by its indented changes.
     *
     * @return preview lines in display order
     */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        for (TreeItem<Item> file : getRoot().getChildren()) {
            lines.add(file.getValue().text());
            file.getChildren().forEach(change -> lines.add("  " + change.getValue().text()));
        }

        return lines;
    }

    /**
     * Describes a change by the lines it touches before the edit and the same lines after it, so that inserted and
     * removed lines are shown as well as changes within a line.
     */
    private static String describe(
        WorkspaceEditPreview.DocumentPreview document,
        WorkspaceEditPreview.ChangePreview change
    ) {
        String before = document.before();
        String after = document.after();
        TextRange replaced = change.before();
        TextRange inserted = change.after();
        // Ranges ending just after a line break end that line rather than touching the next one.
        boolean replacesLines = endsWithLineBreak(before, replaced);
        int beforeEnd = replacesLines ? replaced.end - 1 : lineEnd(before, replaced.end);
        int afterStart = lineStart(after, inserted.start);
        boolean insertsLines = endsWithLineBreak(after, inserted)
            || inserted.start == inserted.end && inserted.start == afterStart;
        int afterEnd = replacesLines && insertsLines
            ? Math.max(afterStart, inserted.end - 1)
            : lineEnd(after, inserted.end);
        return (lineNumber(before, replaced.start) + 1) + ":  "
            + excerpt(before, lineStart(before, replaced.start), beforeEnd) + "  →  "
            + excerpt(after, afterStart, afterEnd);
    }

    private static int lineNumber(String text, int offset) {
        int line = 0;
        for (int index = 0; index < offset && index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                line++;
            }
        }

        return line;
    }

    private static boolean endsWithLineBreak(String text, TextRange range) {
        return range.end > range.start && text.charAt(range.end - 1) == '\n';
    }

    private static int lineStart(String text, int offset) {
        return text.lastIndexOf('\n', offset - 1) + 1;
    }

    private static int lineEnd(String text, int offset) {
        int end = text.indexOf('\n', offset);
        return end < 0 ? text.length() : end;
    }

    private static String excerpt(String text, int start, int end) {
        return text.substring(start, end).lines()
            .map(String::strip)
            .filter(line -> !line.isEmpty())
            .collect(Collectors.joining(" ⏎ "));
    }

    private static String fileName(@Nullable DocumentUri uri) {
        return uri == null ? "" : uri.fileName().orElse(uri.toString());
    }

    /**
     * One shown line.
     *
     * @param text displayed text
     * @param file whether the line names a file rather than a change
     */
    public record Item(String text, boolean file) {
    }

    private static final class ItemCell extends TreeCell<Item> {
        @Override
        protected void updateItem(Item item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().remove("workspace-edit-preview-file");
            setText(empty || item == null ? null : item.text());
            if (!empty && item != null && item.file()) {
                getStyleClass().add("workspace-edit-preview-file");
            }
        }
    }
}
