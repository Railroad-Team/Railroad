package dev.railroadide.railroad.ide.ui;

import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.theme.ThemeManager;
import io.github.raghultech.markdown.javafx.preview.MarkdownWebView;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
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
        } catch (IOException exception) {
            Railroad.LOGGER.error("Unable to read markdown file when previewing.", exception);
            throw new RuntimeException(exception);
        }

        markdownButtons = createMarkdownButtons();
        HBox.setHgrow(markdownButtons, Priority.ALWAYS);
        switchButtons = createViewButtons();

        topRow = new RRHBox(markdownButtons, switchButtons);

        preview.setDarkMode(ThemeManager.getTheme().contains("dark"));

        Settings.THEME.addListener((ignored, newThemeName) ->
            preview.setDarkMode(newThemeName.contains("dark")));

        showContent(splitView(), topRow);
    }

    private TextEditorPane codeView() {
        if (textEditorPane != null)
            return textEditorPane;

        textEditorPane = new TextEditorPane(markdownFile);
        textEditorPane.textProperty().addListener(
            (observable, oldValue, newValue) -> preview.setContent(newValue));

        return textEditorPane;
    }

    private SplitPane splitView() {
        var splitPane = new SplitPane(codeView(), previewView());
        // ensure divider is applied after layout to avoid a 0 width child
        Platform.runLater(() -> splitPane.setDividerPosition(0, 0.5));
        return splitPane;
    }

    private WebView previewView() {
        if (webViewPane != null)
            return webViewPane;

        webViewPane = preview.launch();
        return webViewPane;
    }

    private void showContent(Node content, HBox topRow) {
        getChildren().clear();
        getChildren().addAll(topRow, content);
        VBox.setVgrow(content, Priority.ALWAYS);
        if (textEditorPane != null)
            textEditorPane.scrollToPixel(0, 0);
    }

    private HBox createViewButtons(){
        Button codeView = createButton(FontAwesomeSolid.CODE);
        Button splitView = createButton(FontAwesomeSolid.COLUMNS);
        Button previewView = createButton(FontAwesomeBrands.MARKDOWN);

        var switchButtons = new RRHBox(codeView, splitView, previewView);
        switchButtons.setAlignment(Pos.TOP_RIGHT);

        codeView.setOnAction($ -> {
            markdownButtons.setVisible(true);
            showContent(codeView(), topRow);
        });

        splitView.setOnAction($ -> {
            markdownButtons.setVisible(true);
            showContent(splitView(), topRow);
        });

        previewView.setOnAction($ -> {
            // hide markdown buttons in preview-only mode
            markdownButtons.setVisible(false);
            showContent(previewView(), topRow);
        });

        return switchButtons;
    }

    private HBox createMarkdownButtons(){
        Button headingButton = createButton(FontAwesomeSolid.HEADING);

        var headingMenu = new ContextMenu();

        int[] headingFontSizes = {32, 24, 18, 16, 13, 10};

        for (int level = 1; level <= 6; level++) {
            CustomMenuItem item = createMenuItem(level, headingFontSizes, headingMenu);

            headingMenu.getItems().add(item);
        }

        headingButton.setOnAction($ -> {
            if (!headingMenu.isShowing()) {
                headingMenu.show(headingButton, Side.BOTTOM, 0, 0);
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

        //TODO implement task list in markdown
        //Button taskListButton = createButton(FontAwesomeSolid.TASKS);
        //setButtonOnAction(taskListButton, "- [ ]");


        return new RRHBox(headingButton, boldButton, italicButton, quoteButton, codeButton, linkButton,
            unorderedListButton, orderedListButton);
    }

    private CustomMenuItem createMenuItem(int level, int[] headingFontSizes, ContextMenu headingMenu) {
        String labelText = "H" + level;

        var preview = new Label(labelText);
        preview.getStyleClass().add("markdown-preview-heading-label");
        preview.setStyle("-fx-font-size: " + headingFontSizes[level - 1] + "px;");

        var item = new CustomMenuItem(preview, true);

        item.setOnAction($ -> {
            headingMenu.hide();
            textEditorPane.insertText(textEditorPane.getCaretPosition(), "#".repeat(level) + " ");
            textEditorPane.requestFocus();
        });

        return item;
    }

    private Button createButton(Ikon icon){
        var button = new RRButton("", icon);
        button.setSquare(true);
        button.setRounded(false);
        return button;
    }

    private void setButtonOnAction(Button button, String prefix){
        button.setOnAction($ -> {
            if (textEditorPane != null){
                textEditorPane.insertText(textEditorPane.getCaretPosition(), prefix + " ");
                textEditorPane.requestFocus();
            }
        });
    }

    private void setButtonOnAction(Button button, String prefix, String postfix){
        button.setOnAction($ -> {
            if (textEditorPane != null) {
                int caretPosition = textEditorPane.getCaretPosition();
                textEditorPane.insertText(caretPosition, prefix + postfix);
                textEditorPane.moveTo(caretPosition + prefix.length());
                textEditorPane.requestFocus();
            }
        });
    }
}
