package dev.railroadide.railroad.ide.ui;

import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.railroad.Railroad;
import io.github.raghultech.markdown.javafx.preview.MarkdownWebView;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.web.WebView;
import org.kordamp.ikonli.fontawesome6.FontAwesomeBrands;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

    public class MarkdownPreviewPane extends RRVBox {
    private final MarkdownWebView preview;
    private final Path markdownFile;

    private final Button codeView = new RRButton("", FontAwesomeSolid.CODE);
    private final Button splitView = new RRButton("", FontAwesomeSolid.COLUMNS);
    private final Button previewView = new RRButton("", FontAwesomeBrands.MARKDOWN);

    public MarkdownPreviewPane(Path markdownFile) {
        this.markdownFile = markdownFile;

        try {
            preview = new MarkdownWebView(Files.readString(markdownFile), Railroad.getHostServicess());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        HBox switchButtons = new HBox(codeView, splitView, previewView);
        switchButtons.setAlignment(Pos.TOP_RIGHT);
        HBox markdownButtons = new HBox();
        HBox topRow = new HBox(markdownButtons, switchButtons);
        HBox.setHgrow(markdownButtons, Priority.ALWAYS);

        getChildren().addAll(topRow, splitView());

        codeView.setOnAction(v -> {
            getChildren().clear();
            getChildren().addAll(topRow, codeView());
        });

        splitView.setOnAction(v -> {
            getChildren().clear();
            getChildren().addAll(topRow, splitView());
        });

        previewView.setOnAction(v -> {
            getChildren().clear();
            getChildren().addAll(topRow, previewView());
            markdownButtons.setVisible(false);
        });
    }

    private TextEditorPane codeView(){
        return new TextEditorPane(markdownFile);
    }

    private SplitPane splitView(){
        SplitPane splitPane = new SplitPane(new TextEditorPane(markdownFile), previewView());
        splitPane.setDividerPosition(0, 0.5);
        return splitPane;
    }

    private WebView previewView(){
        return preview.launch();
    }
}
