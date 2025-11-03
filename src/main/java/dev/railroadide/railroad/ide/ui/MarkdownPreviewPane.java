package dev.railroadide.railroad.ide.ui;

import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.railroad.Railroad;
import io.github.raghultech.markdown.javafx.preview.MarkdownWebView;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
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

    public MarkdownPreviewPane(Path markdownFile) {
        this.markdownFile = markdownFile;

        try {
            preview = new MarkdownWebView(Files.readString(markdownFile), Railroad.getHostServicess());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Button codeView = new RRButton("", FontAwesomeSolid.CODE);
        Button splitView = new RRButton("", FontAwesomeSolid.COLUMNS);
        Button previewView = new RRButton("", FontAwesomeBrands.MARKDOWN);

        HBox switchButtons = new HBox(codeView, splitView, previewView);
        switchButtons.setAlignment(Pos.TOP_RIGHT);
        HBox markdownButtons = new HBox();
        HBox topRow = new HBox(markdownButtons, switchButtons);
        HBox.setHgrow(markdownButtons, Priority.ALWAYS);

        // show initial split view and make it grow
        showContent(splitView(), topRow);

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
}
