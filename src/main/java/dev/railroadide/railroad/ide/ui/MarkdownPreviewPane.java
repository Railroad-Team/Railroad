package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.data.ProjectDataStore;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.theme.ThemeManager;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRTextField;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.styling.ButtonSize;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.window.DialogBuilder;
import dev.railroadide.railroad.window.WindowBuilder;
import io.github.raghultech.markdown.javafx.preview.MarkdownWebView;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import lombok.Getter;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeBrands;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

public class MarkdownPreviewPane extends RRVBox {
    public static final Pattern NUMBERED_LIST_PATTERN = Pattern.compile("\\d+\\. \\w+");
    public static final Pattern BULLET_LIST_PATTERN = Pattern.compile("\\* .+");
    public static final Pattern DASH_LIST_PATTERN = Pattern.compile("- .+");
    private static final Pattern HTML_LIST_ITEM_PATTERN = Pattern.compile("\\s*<li>.*</li>\\s*");

    private static final MarkdownLayoutType DEFAULT_MARKDOWN_LAYOUT = MarkdownLayoutType.SPLIT;
    private static final String MARKDOWN_LAYOUT_LOCATION = "markdown.json";

    private final MarkdownWebView preview;
    @Getter private final Path markdownFile;

    private TextEditorPane textEditorPane;
    private WebView webViewPane;

    private final HBox topRow;
    private final HBox markdownButtons;
    private final HBox switchButtons;

    private final Project project;

    private int scrollAmount;

    public MarkdownPreviewPane(Path markdownFile, Project project) {
        this.markdownFile = markdownFile;
        this.project = project;

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

        ProjectDataStore dataStore = project.getDataStore();

        Optional<MarkdownLayout> markdownLayout = dataStore.readJson(MARKDOWN_LAYOUT_LOCATION, MarkdownLayout.class);

        MarkdownLayout layout = markdownLayout.orElseGet(() -> new MarkdownLayout(DEFAULT_MARKDOWN_LAYOUT));
        markdownButtons.setVisible(layout.layoutType() != MarkdownLayoutType.PREVIEW);

        Node pane = switch (layout.layoutType()) {
            case MarkdownLayoutType.CODE -> codeView();
            case MarkdownLayoutType.PREVIEW -> previewView();
            default -> splitView();
        };

        showContent(pane, topRow);
    }

    private TextEditorPane codeView() {
        if (textEditorPane != null) {
            restoreEditorScroll();
            return textEditorPane;
        }


        textEditorPane = new TextEditorPane(markdownFile, "markdown");
        textEditorPane.textProperty().addListener(
            (observable, oldValue, newValue) -> preview.setContent(newValue));

        textEditorPane.addEventFilter(KeyEvent.KEY_PRESSED,event -> {
            if (event.getCode().equals(KeyCode.ENTER)) {
                int caret = textEditorPane.getCaretPosition();
                String upToCaret = textEditorPane.getText(0, caret);
                String lastLine = upToCaret.substring(upToCaret.lastIndexOf("\n") + 1);

                if (NUMBERED_LIST_PATTERN.matcher(lastLine).matches()) {
                    event.consume();

                    int previousLineNumber = Integer.parseInt(lastLine.split("\\.")[0]);
                    textEditorPane.insertText(caret, "\n" + (previousLineNumber + 1) + ". ");
                }

                if (BULLET_LIST_PATTERN.matcher(lastLine).matches()){
                    event.consume();

                    textEditorPane.insertText(caret, "\n* ");
                }

                if (DASH_LIST_PATTERN.matcher(lastLine).matches()) {
                    event.consume();

                    textEditorPane.insertText(caret, "\n- ");
                }

                if (HTML_LIST_ITEM_PATTERN.matcher(lastLine).matches()) {
                    event.consume();

                    String item = "\n<li></li>";
                    textEditorPane.insertText(caret, item);
                    textEditorPane.moveTo(caret + "\n<li>".length());
                }
            }
        });

        textEditorPane.addEventFilter(ScrollEvent.SCROLL, event ->
            scrollAmount = (int) textEditorPane.getEstimatedScrollY());

        restoreEditorScroll();

        return textEditorPane;
    }

    private SplitPane splitView() {
        var splitPane = new SplitPane(codeView(), previewView());
        // ensure divider is applied after layout to avoid a 0 width child
        Platform.runLater(() -> {
            splitPane.setDividerPosition(0, 0.5);
            restoreEditorScroll();
        });
        return splitPane;
    }

    private WebView previewView() {
        if (webViewPane != null)
            return webViewPane;

        webViewPane = preview.launch();
        return webViewPane;
    }

    private void switchLayout(MarkdownLayoutType layoutType) {
        getChildren().clear();

        Node pane = switch (layoutType) {
            case CODE -> codeView();
            case PREVIEW -> previewView();
            case SPLIT -> splitView();
        };

        markdownButtons.setVisible(layoutType != MarkdownLayoutType.PREVIEW);
        getChildren().addAll(topRow, pane);
        VBox.setVgrow(pane, Priority.ALWAYS);
    }

    private void showContent(Node content, HBox topRow) {
        getChildren().clear();
        getChildren().addAll(topRow, content);
        VBox.setVgrow(content, Priority.ALWAYS);
    }

    private void restoreEditorScroll() {
        if (textEditorPane != null)
            Platform.runLater(() -> textEditorPane.scrollToPixel(0, scrollAmount));
    }

    private HBox createViewButtons() {
        Button codeView = createButton(FontAwesomeSolid.CODE);
        Button splitView = createButton(FontAwesomeSolid.COLUMNS);
        Button previewView = createButton(FontAwesomeBrands.MARKDOWN);

        var switchButtons = new RRHBox(codeView, splitView, previewView);
        switchButtons.setAlignment(Pos.TOP_RIGHT);

        codeView.setOnAction($ -> {
            switchLayout(MarkdownLayoutType.CODE);
            showContent(codeView(), topRow);
            project.getDataStore().writeJson(MARKDOWN_LAYOUT_LOCATION, new MarkdownLayout(MarkdownLayoutType.CODE));
        });

        splitView.setOnAction($ -> {
            switchLayout(MarkdownLayoutType.SPLIT);
            showContent(splitView(), topRow);
            project.getDataStore().writeJson(MARKDOWN_LAYOUT_LOCATION, new MarkdownLayout(MarkdownLayoutType.SPLIT));
        });

        previewView.setOnAction($ -> {
            // hide markdown buttons in preview-only mode
            switchLayout(MarkdownLayoutType.PREVIEW);
            showContent(previewView(), topRow);
            project.getDataStore().writeJson(MARKDOWN_LAYOUT_LOCATION, new MarkdownLayout(MarkdownLayoutType.PREVIEW));
        });

        return switchButtons;
    }

    private HBox createMarkdownButtons() {
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

        Button taskListButton = createButton(FontAwesomeSolid.TASKS);
        setButtonOnAction(taskListButton, "- [ ]");

        Button horizontalRuleButton = createButton(FontAwesomeSolid.MINUS);
        setButtonOnAction(horizontalRuleButton, "---");

        Button strikethroughButton = createButton(FontAwesomeSolid.STRIKETHROUGH);
        setButtonOnAction(strikethroughButton, "~~", "~~");

        Button codeBlockButton = createButton(FontAwesomeSolid.CODE);
        setButtonOnAction(codeBlockButton, "```", "```");

        Button imageButton = createButton(FontAwesomeSolid.IMAGE);
        imageButton.setOnAction(event -> imageDialog());

        return new RRHBox(headingButton, boldButton, italicButton, quoteButton, codeButton, linkButton,
            unorderedListButton, orderedListButton, taskListButton, horizontalRuleButton, strikethroughButton, codeBlockButton, imageButton);
    }

    private CustomMenuItem createMenuItem(int level, int[] headingFontSizes, ContextMenu headingMenu) {
        String labelText = "H" + level;

        var preview = new Label(labelText);
        preview.getStyleClass().add("markdown-preview-heading-label");
        preview.setStyle("-fx-font-size: " + headingFontSizes[level - 1] + "px;");

        var item = new CustomMenuItem(preview, true);

        item.setOnAction($ -> {
            headingMenu.hide();
            TextEditorPane editor = editorForInsertion();
            editor.insertText(editor.getCaretPosition(), "#".repeat(level) + " ");
            editor.requestFocus();
        });

        return item;
    }

    private Button createButton(Ikon icon) {
        var button = new RRButton("", icon);
        button.setSquare(true);
        button.setRounded(false);
        return button;
    }

    private void setButtonOnAction(Button button, String prefix) {
        button.setOnAction($ -> {
            TextEditorPane editor = editorForInsertion();
            editor.insertText(editor.getCaretPosition(), prefix + " ");
            editor.requestFocus();
        });
    }

    private void setButtonOnAction(Button button, String prefix, String postfix) {
        button.setOnAction($ -> {
            TextEditorPane editor = editorForInsertion();
            int caretPosition = editor.getCaretPosition();
            editor.insertText(caretPosition, prefix + postfix);
            editor.moveTo(caretPosition + prefix.length());
            editor.requestFocus();
        });
    }

    private TextEditorPane editorForInsertion() {
        return textEditorPane == null ? codeView() : textEditorPane;
    }

    private void imageDialog() {
        var altTextField = new RRTextField("railroad.markdown.image_dialog.image_alt_text", FontAwesomeSolid.IMAGE);
        var uriTextField = new RRTextField("railroad.markdown.image_dialog.image_uri_prompt", FontAwesomeSolid.LINK);

        altTextField.setPrefColumnCount(42);
        uriTextField.setPrefColumnCount(42);
        altTextField.getStyleClass().add("markdown-image-dialog-field");
        uriTextField.getStyleClass().add("markdown-image-dialog-field");

        if (textEditorPane != null && !textEditorPane.getSelectedText().isBlank()) {
            altTextField.setText(textEditorPane.getSelectedText());
        }

        var form = new RRVBox(22, altTextField, uriTextField);
        form.getStyleClass().add("markdown-image-dialog-form");
        form.setPadding(new Insets(22, 32, 22, 32));

        var cancelButton = new RRButton("railroad.generic.cancel");
        var insertButton = new RRButton("railroad.markdown.image_dialog.insert", FontAwesomeSolid.IMAGE);

        createDialogButtons(cancelButton, insertButton, uriTextField, altTextField);

        var dialog = WindowBuilder.createDialog(
            "railroad.markdown.image_dialog.heading",
            DialogBuilder.create()
                .title("railroad.markdown.image_dialog.heading")
                .contentNode(form)
                .buttons(cancelButton, insertButton)
                .submitOnEnter(false)
        );
        Platform.runLater(() -> {
            addNodeStyles(dialog);
            dialog.getScene().getRoot().applyCss();
            dialog.getScene().getRoot().layout();
            dialog.sizeToScene();
        });
    }

    private static void addNodeStyles(Stage dialog) {
        var title = dialog.getScene().lookup(".alert-title");
        if (title != null) {
            title.getStyleClass().add("markdown-image-dialog-title");
        }

        var header = dialog.getScene().lookup(".alert-header");
        if (header != null) {
            header.getStyleClass().add("markdown-image-dialog-header");
        }

        var buttons = dialog.getScene().lookup(".alert-buttons");
        if (buttons != null) {
            buttons.getStyleClass().add("markdown-image-dialog-buttons");
        }
    }

    private void createDialogButtons(RRButton cancelButton, RRButton insertButton, RRTextField uriTextField, RRTextField altTextField){
        insertButton.setVariant(ButtonVariant.PRIMARY);
        insertButton.setButtonSize(ButtonSize.LARGE);
        insertButton.getStyleClass().add("markdown-image-dialog-button");
        insertButton.setDefaultButton(true);
        insertButton.disableProperty().bind(Bindings.createBooleanBinding(
            () -> uriTextField.getText().trim().isEmpty(),
            uriTextField.textProperty()
        ));

        cancelButton.setVariant(ButtonVariant.SECONDARY);
        cancelButton.setButtonSize(ButtonSize.LARGE);
        cancelButton.getStyleClass().add("markdown-image-dialog-button");

        cancelButton.setOnAction(event -> {
            if (insertButton.getScene().getWindow() instanceof Stage stage) {
                stage.close();
            }
        });

        insertButton.setOnAction(event -> {
            insertMarkdownImage(altTextField.getText(), uriTextField.getText());
            if (insertButton.getScene().getWindow() instanceof Stage stage) {
                stage.close();
            }
        });
    }

    private void insertMarkdownImage(String altText, String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            return;
        }

        uri = uri.trim()
            .replace("\\", "\\\\")
            .replace(")", "\\)");

        altText = altText.trim()
            .replace("\\", "\\\\")
            .replace("[", "\\[")
            .replace("]", "\\]");

        TextEditorPane editor = editorForInsertion();
        String markdown = "![" + altText + "](" + uri + ")";
        IndexRange selection = editor.getSelection();
        editor.replaceText(selection.getStart(), selection.getEnd(), markdown);
        editor.requestFocus();
    }

    public record MarkdownLayout(MarkdownLayoutType layoutType) { }

    public enum MarkdownLayoutType {
        SPLIT,
        PREVIEW,
        CODE
    }
}
