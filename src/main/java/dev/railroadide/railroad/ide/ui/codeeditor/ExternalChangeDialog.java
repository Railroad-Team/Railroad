package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.window.WindowBuilder;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.nio.charset.StandardCharsets;

/**
 * Presents conflicting editor and disk contents with reload and keep-local actions.
 */
public final class ExternalChangeDialog implements AutoCloseable {
    private final Stage stage;
    private final RRVBox content;
    private final LocalizedLabel message;
    private final String editorStyle;
    private CodeArea mineArea;
    private CodeArea diskArea;
    private String mine;
    private String disk;

    /**
     * Creates the external-change dialog and its resolution actions.
     *
     * @param editor editor containing the local version of the file
     * @param reload action that accepts the disk version
     * @param keepMine action that retains the editor version
     */
    public ExternalChangeDialog(TextEditorPane editor, Runnable reload, Runnable keepMine) {
        editorStyle = editor.getStyle();
        var title = new LocalizedLabel("editor.external_change.title");
        message = new LocalizedLabel("editor.external_change.message", editor.getFilePath());
        message.setWrapText(true);

        var reloadButton = new RRButton("editor.external_change.reload");
        reloadButton.setVariant(ButtonVariant.DANGER);
        reloadButton.setOnAction(_ -> reload.run());
        var keepButton = new RRButton("editor.external_change.keep_mine");
        keepButton.setVariant(ButtonVariant.PRIMARY);
        keepButton.setOnAction(_ -> keepMine.run());
        var compareButton = new RRButton("editor.external_change.compare");
        compareButton.setVariant(ButtonVariant.SECONDARY);
        compareButton.setOnAction(_ -> {
            showComparison();
            compareButton.setVisible(false);
            compareButton.setManaged(false);
        });
        var buttons = new RRHBox(10, reloadButton, keepButton, compareButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        content = new RRVBox(16, title, message, buttons);
        content.setPadding(new Insets(24));
        stage = WindowBuilder.create()
            .title("editor.external_change.title", true)
            .owner(editor.getScene() == null ? null : editor.getScene().getWindow())
            .modality(Modality.WINDOW_MODAL)
            .scene(new Scene(content))
            .size(680, 240)
            .minSize(580, 240)
            .build();
        stage.setOnCloseRequest(event -> event.consume());
    }

    /**
     * Replaces the editor and disk snapshots and refreshes any visible comparison.
     *
     * @param mine current editor contents
     * @param disk current contents read from disk
     */
    public void update(String mine, String disk) {
        this.mine = mine;
        this.disk = disk;
        if (mineArea != null) {
            updateComparison();
        }
    }

    /**
     * Displays the message indicating that the disk version could not be read.
     */
    public void showReadError() {
        message.setKey("editor.external_change.read_error");
    }

    private void showComparison() {
        if (mineArea == null) {
            mineArea = createArea();
            diskArea = createArea();
            var comparison = new SplitPane(
                comparisonColumn("editor.external_change.mine", mineArea),
                comparisonColumn("editor.external_change.disk", diskArea));
            comparison.setDividerPositions(0.5);
            RRVBox.setVgrow(comparison, Priority.ALWAYS);
            content.getChildren().add(2, comparison);
            stage.setWidth(1050);
            stage.setHeight(650);
        }
        updateComparison();
    }

    private CodeArea createArea() {
        var area = new CodeArea();
        area.setEditable(false);
        area.setStyle(editorStyle);
        area.setParagraphGraphicFactory(LineNumberFactory.get(area));
        return area;
    }

    private static RRVBox comparisonColumn(String title, CodeArea area) {
        var scroll = new VirtualizedScrollPane<>(area);
        RRVBox.setVgrow(scroll, Priority.ALWAYS);
        return new RRVBox(8, new LocalizedLabel(title), scroll);
    }

    private void updateComparison() {
        mineArea.replaceText(mine);
        diskArea.replaceText(disk);
        var edits = new HistogramDiff().diff(RawTextComparator.DEFAULT,
            new RawText(mine.getBytes(StandardCharsets.UTF_8)),
            new RawText(disk.getBytes(StandardCharsets.UTF_8)));
        for (var edit : edits) {
            highlightLines(mineArea, edit.getBeginA(), edit.getEndA(), "git-diff-line-deletion");
            highlightLines(diskArea, edit.getBeginB(), edit.getEndB(), "git-diff-line-addition");
        }
    }

    private static void highlightLines(CodeArea area, int start, int end, String style) {
        for (int line = start; line < end; line++) {
            int offset = area.getAbsolutePosition(line, 0);
            area.setStyleClass(offset, offset + area.getParagraphLength(line), style);
        }
    }

    @Override
    public void close() {
        stage.hide();
    }
}
