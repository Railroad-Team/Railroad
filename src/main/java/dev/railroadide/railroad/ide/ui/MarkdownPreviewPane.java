package dev.railroadide.railroad.ide.ui;

import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.railroad.Railroad;
import io.github.raghultech.markdown.javafx.preview.MarkdownWebView;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeBrands;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MarkdownPreviewPane extends RRVBox {
    private final MarkdownWebView preview;
    private final Path markdownFile;

    private TextEditorPane textEditorPane;
    private WebView webViewPane;

    private final HBox topRow;
    private final HBox markdownButtons;
    private final HBox switchButtons;

    public MarkdownPreviewPane(Path markdownFile) {
        this.markdownFile = markdownFile;

        try {
            preview = new MarkdownWebView(Files.readString(markdownFile), Railroad.getHostServicess());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        markdownButtons = createMarkdownButtons();
        HBox.setHgrow(markdownButtons, Priority.ALWAYS);
        switchButtons = createViewButtons();

        topRow = new HBox(markdownButtons, switchButtons);

        showContent(splitView(), topRow);
    }

    private TextEditorPane codeView() {
        if(textEditorPane != null)
            return textEditorPane;

        textEditorPane = new TextEditorPane(markdownFile);
        textEditorPane.textProperty().addListener(
            (observable, oldValue, newValue) -> preview.setContent(newValue));

        return textEditorPane;
    }

    private SplitPane splitView() {
        SplitPane splitPane = new SplitPane(codeView(), previewView());
        // ensure divider is applied after layout to avoid a 0 width child
        Platform.runLater(() -> splitPane.setDividerPosition(0, 0.5));
        return splitPane;
    }

    private WebView previewView() {
        if(webViewPane != null)
            return webViewPane;

        webViewPane = preview.launch();
        return webViewPane;
    }

    // helper that replaces the content below the top row and makes it grow
    private void showContent(Node content, HBox topRow) {
        getChildren().clear();
        getChildren().addAll(topRow, content);
        VBox.setVgrow(content, Priority.ALWAYS);
        textEditorPane.scrollToPixel(0, 0);
    }

    private HBox createViewButtons(){
        Button codeView = createButton(FontAwesomeSolid.CODE);
        Button splitView = createButton(FontAwesomeSolid.COLUMNS);
        Button previewView = createButton(FontAwesomeBrands.MARKDOWN);

        HBox switchButtons = new HBox(codeView, splitView, previewView);
        switchButtons.setAlignment(Pos.TOP_RIGHT);

        codeView.setOnAction(v -> {
            // restore markdown buttons when switching back from preview
            markdownButtons.setVisible(true);
            showContent(codeView(), topRow);
        });

        splitView.setOnAction(v -> {
            markdownButtons.setVisible(true);
            showContent(splitView(), topRow);
        });

        previewView.setOnAction(v -> {
            // hide markdown buttons in preview-only mode
            markdownButtons.setVisible(false);
            showContent(previewView(), topRow);
        });

        return switchButtons;
    }

    private HBox createMarkdownButtons(){
        Button headingButton = createButton(FontAwesomeSolid.HEADING);

        var headingMenu = new javafx.scene.control.ContextMenu();

        // example font sizes for H1..H5 (adjust as desired)
        int[] headingFontSizes = {32, 24, 18, 16, 13, 10};

        for (int level = 1; level <= 6; level++) {
            CustomMenuItem item = createMenuItem(level, headingFontSizes, headingMenu);

            headingMenu.getItems().add(item);
        }

        headingButton.setOnAction(e -> {
            if (!headingMenu.isShowing()) {
                headingMenu.show(headingButton, javafx.geometry.Side.BOTTOM, 0, 0);
            } else {
                headingMenu.hide();
            }
        });


        Button boldButton = createButton(FontAwesomeSolid.BOLD);
        setButtonOnAction(boldButton, "**", "**");
        Button italicButton = createButton(FontAwesomeSolid.ITALIC);
        setButtonOnAction(italicButton, "_", "_");

        Button quoteButton = createButton(FontAwesomeSolid.QUOTE_LEFT);
        setButtonOnAction(quoteButton, "> ");
        Button codeButton = createButton(FontAwesomeSolid.CODE);
        setButtonOnAction(codeButton, "`", "`");
        Button linkButton = createButton(FontAwesomeSolid.LINK);
        setButtonOnAction(linkButton, "[", "](url)");

        Button unorderedListButton = createButton(FontAwesomeSolid.LIST_UL);
        setButtonOnAction(unorderedListButton, "- ");
        Button orderedListButton = createButton(FontAwesomeSolid.LIST_OL);
        setButtonOnAction(orderedListButton, "1. ");
        //Button taskListButton = createButton(FontAwesomeSolid.TASKS);
        //setButtonOnAction(taskListButton, "- [ ]");


        return new HBox(headingButton, boldButton, italicButton, quoteButton, codeButton, linkButton,
            unorderedListButton, orderedListButton/*, taskListButton*/);
    }

    private CustomMenuItem createMenuItem(int level, int[] headingFontSizes, ContextMenu headingMenu) {
        String labelText = "H" + level;

        javafx.scene.control.Label preview = new javafx.scene.control.Label(labelText);
        // monospace + padding + approximate size per heading level
        preview.setStyle("-fx-font-family: 'monospace'; -fx-padding: 6 12; -fx-font-size: " + headingFontSizes[level - 1] + "px;");

        CustomMenuItem item = new CustomMenuItem(preview, true);
        // hide menu when clicked; replace body with your insertion logic later

        item.setOnAction(e -> {
            headingMenu.hide();
            textEditorPane.insertText(textEditorPane.getCaretPosition(), "#".repeat(level) + " ");
            textEditorPane.requestFocus();
        });
        return item;
    }

    private Button createButton(Ikon icon){
        RRButton button = new RRButton("", icon);
        button.setSquare(true);
        button.setRounded(false);
        return button;
    }

    private void setButtonOnAction(Button button, String prefix){
        button.setOnAction(v -> {
            textEditorPane.insertText(textEditorPane.getCaretPosition(), prefix + " ");
            textEditorPane.requestFocus();
        });
    }

    private void setButtonOnAction(Button button, String prefix, String postfix){
        button.setOnAction(v -> {
            int caretPosition = textEditorPane.getCaretPosition();
            textEditorPane.insertText(caretPosition, prefix + postfix);
            textEditorPane.moveTo(caretPosition + prefix.length());
            textEditorPane.requestFocus();
        });
    }
}
