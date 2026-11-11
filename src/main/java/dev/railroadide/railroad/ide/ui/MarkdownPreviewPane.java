package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.command.CommandButtons;
import dev.railroadide.railroad.command.CommandContext;
import dev.railroadide.railroad.command.CommandMenuItems;
import dev.railroadide.railroad.command.MarkdownCommands;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.plugin.spi.editor.*;
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
import dev.railroadide.railroad.utility.javafx.JavaFXUtils;
import io.github.raghultech.markdown.javafx.preview.MarkdownWebView;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.event.EventHandler;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Displays Markdown source, rendered preview, or both with editing and layout controls.
 */
public class MarkdownPreviewPane extends RRVBox implements AutoCloseable {
    /**
     * Matches a decimal list marker followed by a word.
     */
    public static final Pattern NUMBERED_LIST_PATTERN = Pattern.compile("\\d+\\. \\w+");
    /**
     * Matches an asterisk list marker followed by text.
     */
    public static final Pattern BULLET_LIST_PATTERN = Pattern.compile("\\* .+");
    /**
     * Matches a dash list marker followed by text.
     */
    public static final Pattern DASH_LIST_PATTERN = Pattern.compile("- .+");
    private static final Pattern HTML_LIST_ITEM_PATTERN = Pattern.compile("\\s*<li>.*</li>\\s*");

    private static final MarkdownLayoutType DEFAULT_MARKDOWN_LAYOUT = MarkdownLayoutType.SPLIT;
    private static final String MARKDOWN_LAYOUT_LOCATION = "markdown.json";

    private final MarkdownWebView preview;
    private final DocumentId documentId;
    @Getter
    private Path markdownFile;
    private boolean closed;
    private final String initialContent;
    private DocumentStore documentStore;
    private DocumentStore.ChangeSubscription storeSubscription;
    private final AtomicBoolean refreshQueued = new AtomicBoolean();
    private final List<SnapshotObserver> snapshotObservers = new ArrayList<>();
    private final List<SelectionObserver> selectionObservers = new ArrayList<>();
    private final TextEditorAccess textEditorAccess = new MarkdownTextAccess();
    private EventHandler<KeyEvent> sourceKeyHandler;
    private EventHandler<ScrollEvent> sourceScrollHandler;
    private MarkdownLayoutType currentLayout;
    private String renderedText;

    private WebView webViewPane;

    private final HBox topRow;
    private final HBox markdownButtons;
    private final HBox switchButtons;

    private final Project project;
    private final BiConsumer<String, String> themeListener;

    private int scrollAmount;

    private final ObjectProperty<TextEditorPane> editor = new SimpleObjectProperty<>();

    /**
     * Returns the property holding the Markdown source editor.
     * The value is {@code null} until the editor is created and is cleared
     * when this pane is closed.
     *
     * @return the non-null property holding the Markdown source editor
     */
    public ObservableValue<TextEditorPane> editorProperty() {
        return editor;
    }

    @Override
    public void close() {
        if (closed)
            return;
        TextEditorPane textEditorPane = getMarkdownEditorPane();
        if (textEditorPane != null) {
            textEditorPane.close();
            if (!textEditorPane.isEditorClosed())
                return;
            textEditorPane.removeEventFilter(KeyEvent.KEY_PRESSED, sourceKeyHandler);
            textEditorPane.removeEventFilter(ScrollEvent.SCROLL, sourceScrollHandler);
            editor.setValue(null);
        }
        closed = true;
        if (storeSubscription != null) {
            storeSubscription.close();
            storeSubscription = null;
        }
        List.copyOf(snapshotObservers).forEach(SnapshotObserver::close);
        List.copyOf(selectionObservers).forEach(SelectionObserver::close);
        Settings.THEME.removeListener(themeListener);
        if (webViewPane != null) {
            webViewPane.getEngine().load(null);
        }
    }

    /**
     * Updates the source location used when the source editor is created after a move.
     *
     * @param path current document path
     */
    public void rebind(Path path) {
        markdownFile = Objects.requireNonNull(path).toAbsolutePath().normalize();
        if (!closed && documentStore != null && getMarkdownEditorPane() == null
            && !Services.IDE_STATE.isDocumentWatched(markdownFile)) {
            codeView();
        }
    }

    /**
     * Attaches preview rendering to the authoritative document. A retained draft acquires a hidden
     * source editor so existing dirty-state, save and close ownership continue to protect it.
     * Documents outside project watcher coverage also acquire a source for its existing watcher.
     * Clean preview-only documents covered by project watching remain lazy.
     *
     * @param store initialized project document store
     */
    public void setDocumentStore(DocumentStore store) {
        requireEditorThread();
        if (closed || documentStore != null)
            throw new IllegalStateException("Markdown view is closed or already initialized");
        documentStore = Objects.requireNonNull(store);
        if (getMarkdownEditorPane() == null && initialContent != null && store.current(documentId).isEmpty()) {
            DocumentUri uri = DocumentUri.fromPath(markdownFile);
            DocumentVersion version = store.latestVersion(documentId).map(DocumentVersion::next)
                .orElse(DocumentVersion.initial());
            try {
                store.publishBacking(new TextDocumentSnapshot(documentId, uri, version, "markdown",
                    DocumentOrigin.original(uri), initialContent, StandardCharsets.UTF_8), null);
            } catch (IllegalStateException competingPublication) {
                if (store.current(documentId).isEmpty())
                    throw competingPublication;
            }
        }
        if (getMarkdownEditorPane() == null
            && (store.state(documentId).map(state -> state.overlay() != null).orElse(false)
                || !Services.IDE_STATE.isDocumentWatched(markdownFile))) {
            codeView();
        }
        storeSubscription = store.onChange(this::queuePublishedRefresh);
        refreshPublishedContent();
    }

    /** @return stable source capability, including when the source has not been materialized */
    public TextEditorAccess textEditorAccess() {
        return textEditorAccess;
    }

    private static void requireEditorThread() {
        if (!Platform.isFxApplicationThread())
            throw new IllegalStateException("Text editor operations require the editor UI thread");
    }

    private TextDocumentSnapshot publishedSnapshot() {
        if (closed || documentStore == null)
            return null;
        var current = documentStore.current(documentId).orElse(null);
        if (!(current instanceof TextDocumentSnapshot snapshot)
            || !snapshot.uri().equals(DocumentUri.fromPath(markdownFile)))
            return null;
        TextEditorPane source = getMarkdownEditorPane();
        return source == null || !source.isEditorClosed() && source.getText().equals(snapshot.text()) ? snapshot : null;
    }

    private static boolean sameSnapshot(TextDocumentSnapshot first, TextDocumentSnapshot second) {
        return first != null && second != null && first.id().equals(second.id())
            && first.version().equals(second.version()) && first.uri().equals(second.uri())
            && first.languageId().equals(second.languageId()) && first.origin().equals(second.origin())
            && first.encoding().equals(second.encoding()) && first.text().equals(second.text());
    }

    private void queuePublishedRefresh() {
        if (refreshQueued.compareAndSet(false, true)) {
            Platform.runLater(() -> {
                refreshQueued.set(false);
                refreshPublishedContent();
            });
        }
    }

    private void refreshPublishedContent() {
        TextDocumentSnapshot snapshot = publishedSnapshot();
        if (snapshot == null)
            return;
        if (!snapshot.text().equals(renderedText)) {
            renderedText = snapshot.text();
            preview.setContent(renderedText);
        }
        List.copyOf(snapshotObservers).forEach(observer -> observer.deliver(snapshot));
    }

    private TextEditorAccess revealSource() {
        if (closed)
            throw new IllegalStateException("Markdown view is closed");
        if (currentLayout == MarkdownLayoutType.PREVIEW) {
            switchLayout(MarkdownLayoutType.SPLIT);
        }
        return codeView().textEditorAccess();
    }

    private final class MarkdownTextAccess implements TextEditorAccess {
        @Override
        public void reveal() {
            requireEditorThread();
            revealSource().reveal();
        }

        @Override
        public TextDocumentSnapshot snapshot() {
            requireEditorThread();
            TextDocumentSnapshot snapshot = publishedSnapshot();
            if (snapshot == null)
                throw new IllegalStateException("No reconciled published Markdown source is available");
            return snapshot;
        }

        @Override
        public TextSelection selection() {
            requireEditorThread();
            if (closed)
                throw new IllegalStateException("Markdown view is closed");
            TextEditorPane source = getMarkdownEditorPane();
            return source == null ? new TextSelection(0, 0) : source.textEditorAccess().selection();
        }

        private EditResult validateExpected(TextDocumentSnapshot expected) {
            if (closed)
                return EditResult.CLOSED;
            if (expected == null)
                return EditResult.INVALID;
            TextDocumentSnapshot current = publishedSnapshot();
            if (current == null)
                return EditResult.UNAVAILABLE;
            return sameSnapshot(expected, current) ? EditResult.APPLIED : EditResult.STALE;
        }

        @Override
        public EditResult selectAndReveal(TextDocumentSnapshot expected, TextSelection selection) {
            requireEditorThread();
            EditResult validation = validateExpected(expected);
            if (validation != EditResult.APPLIED)
                return validation;
            if (selection == null || selection.anchor() > expected.text().length()
                || selection.caret() > expected.text().length())
                return EditResult.INVALID;
            return revealSource().selectAndReveal(expected, selection);
        }

        @Override
        public boolean isEditable() {
            requireEditorThread();
            TextEditorPane source = getMarkdownEditorPane();
            return !closed && (source == null
                ? !Files.exists(markdownFile) || Files.isWritable(markdownFile)
                : source.textEditorAccess().isEditable());
        }

        @Override
        public EditResult replace(TextDocumentSnapshot expected, List<TextReplacement> replacements) {
            requireEditorThread();
            EditResult validation = validateExpected(expected);
            if (validation != EditResult.APPLIED)
                return validation;
            if (!isEditable())
                return EditResult.READ_ONLY;
            return revealSource().replace(expected, replacements);
        }

        @Override
        public EditorSubscription observe(Consumer<TextDocumentSnapshot> listener) {
            requireEditorThread();
            if (closed || documentStore == null)
                throw new IllegalStateException("Markdown view is closed or not initialized");
            var observer = new SnapshotObserver(Objects.requireNonNull(listener));
            snapshotObservers.add(observer);
            queuePublishedRefresh();
            return observer;
        }

        @Override
        public EditorSubscription observeSelection(Consumer<TextSelection> listener) {
            requireEditorThread();
            if (closed)
                throw new IllegalStateException("Markdown view is closed");
            var observer = new SelectionObserver(Objects.requireNonNull(listener));
            selectionObservers.add(observer);
            observer.bindSource();
            return observer;
        }
    }

    private final class SnapshotObserver implements EditorSubscription {
        private final Consumer<TextDocumentSnapshot> listener;
        private TextDocumentSnapshot previous;
        private volatile boolean disposed;

        private SnapshotObserver(Consumer<TextDocumentSnapshot> listener) {
            this.listener = listener;
        }

        private void deliver(TextDocumentSnapshot snapshot) {
            if (!disposed && !closed && !sameSnapshot(previous, snapshot)) {
                previous = snapshot;
                try {
                    listener.accept(snapshot);
                } catch (RuntimeException exception) {
                    Railroad.LOGGER.warn("Markdown text observer failed", exception);
                }
            }
        }

        @Override
        public void close() {
            disposed = true;
            JavaFXUtils.runOnApplicationThread(() -> snapshotObservers.remove(this));
        }
    }

    private final class SelectionObserver implements EditorSubscription {
        private final Consumer<TextSelection> listener;
        private TextSelection previous;
        private EditorSubscription sourceSubscription;
        private volatile boolean disposed;

        private SelectionObserver(Consumer<TextSelection> listener) {
            this.listener = listener;
        }

        private void bindSource() {
            if (disposed || closed)
                return;
            TextEditorPane source = getMarkdownEditorPane();
            if (source == null) {
                Platform.runLater(() -> {
                    if (!disposed && !closed) {
                        deliver(textEditorAccess.selection());
                    }
                });
            } else if (sourceSubscription == null) {
                sourceSubscription = source.textEditorAccess().observeSelection(this::deliver);
            }
        }

        private void deliver(TextSelection selection) {
            if (!disposed && !closed && !selection.equals(previous)) {
                previous = selection;
                try {
                    listener.accept(selection);
                } catch (RuntimeException exception) {
                    Railroad.LOGGER.warn("Markdown selection observer failed", exception);
                }
            }
        }

        @Override
        public void close() {
            disposed = true;
            JavaFXUtils.runOnApplicationThread(() -> {
                if (sourceSubscription != null) {
                    sourceSubscription.close();
                    sourceSubscription = null;
                }
                selectionObservers.remove(this);
            });
        }
    }

    /**
     * Loads a Markdown file and restores the project preview layout.
     *
     * @param documentId authoritative logical identity retained by the lazy source editor
     * @param markdownFile Markdown file to load and edit
     * @param project project whose files and workspace are being displayed
     */
    public MarkdownPreviewPane(DocumentId documentId, Path markdownFile, Project project) {
        this.documentId = Objects.requireNonNull(documentId, "documentId");
        this.markdownFile = markdownFile;
        this.project = project;

        String loaded = null;
        try {
            loaded = Files.readString(markdownFile);
        } catch (IOException exception) {
            Railroad.LOGGER.error("Unable to read markdown file when previewing.", exception);
        }
        initialContent = loaded;
        renderedText = loaded == null ? "" : loaded;
        preview = new MarkdownWebView(renderedText, Railroad.getHostServicess());

        markdownButtons = createMarkdownButtons();
        HBox.setHgrow(markdownButtons, Priority.ALWAYS);
        switchButtons = createViewButtons();

        topRow = new RRHBox(markdownButtons, switchButtons);

        preview.setDarkMode(ThemeManager.getTheme().contains("dark"));

        themeListener = (_, newThemeName) -> preview.setDarkMode(newThemeName.contains("dark"));
        Settings.THEME.addListener(themeListener);

        ProjectDataStore dataStore = project.getDataStore();

        Optional<MarkdownLayout> markdownLayout = dataStore.readJson(MARKDOWN_LAYOUT_LOCATION, MarkdownLayout.class);

        MarkdownLayout layout = markdownLayout.orElseGet(() -> new MarkdownLayout(DEFAULT_MARKDOWN_LAYOUT));
        currentLayout = layout.layoutType();
        markdownButtons.setVisible(layout.layoutType() != MarkdownLayoutType.PREVIEW);

        Node pane = switch (layout.layoutType()) {
            case MarkdownLayoutType.CODE -> codeView();
            case MarkdownLayoutType.PREVIEW -> previewView();
            default -> splitView();
        };

        showContent(pane, topRow);
    }

    /**
     * Returns the markdown editor pane property
     *
     * @return markdown editor pane (TextEditorPane)
     */
    public TextEditorPane getMarkdownEditorPane() {
        return editor.get();
    }

    private TextEditorPane codeView() {
        if (closed)
            throw new IllegalStateException("Markdown view is closed");
        TextEditorPane textEditorPane = getMarkdownEditorPane();
        if (textEditorPane != null) {
            restoreEditorScroll();
            return textEditorPane;
        }

        editor.setValue(new TextEditorPane(documentId, markdownFile, "markdown"));

        TextEditorPane newTextEditorPane = getMarkdownEditorPane();

        sourceKeyHandler = event -> {
            if (event.getCode().equals(KeyCode.ENTER)) {
                int caret = newTextEditorPane.getCaretPosition();
                String upToCaret = newTextEditorPane.getText(0, caret);
                String lastLine = upToCaret.substring(upToCaret.lastIndexOf("\n") + 1);

                if (NUMBERED_LIST_PATTERN.matcher(lastLine).matches()) {
                    event.consume();

                    int previousLineNumber = Integer.parseInt(lastLine.split("\\.")[0]);
                    newTextEditorPane.insertText(caret, "\n" + (previousLineNumber + 1) + ". ");
                }

                if (BULLET_LIST_PATTERN.matcher(lastLine).matches()) {
                    event.consume();

                    newTextEditorPane.insertText(caret, "\n* ");
                }

                if (DASH_LIST_PATTERN.matcher(lastLine).matches()) {
                    event.consume();

                    newTextEditorPane.insertText(caret, "\n- ");
                }

                if (HTML_LIST_ITEM_PATTERN.matcher(lastLine).matches()) {
                    event.consume();

                    String item = "\n<li></li>";
                    newTextEditorPane.insertText(caret, item);
                    newTextEditorPane.moveTo(caret + "\n<li>".length());
                }
            }
        };
        newTextEditorPane.addEventFilter(KeyEvent.KEY_PRESSED, sourceKeyHandler);
        sourceScrollHandler = _ -> scrollAmount = (int) newTextEditorPane.getEstimatedScrollY();
        newTextEditorPane.addEventFilter(ScrollEvent.SCROLL, sourceScrollHandler);
        List.copyOf(selectionObservers).forEach(SelectionObserver::bindSource);
        queuePublishedRefresh();

        restoreEditorScroll();

        return newTextEditorPane;
    }

    private SplitPane splitView() {
        var splitPane = new SplitPane(codeView(), previewView());
        // ensure divider is applied after layout to avoid a 0 width child
        Platform.runLater(() -> {
            if (closed)
                return;
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
        if (closed)
            throw new IllegalStateException("Markdown view is closed");
        currentLayout = layoutType;
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
        TextEditorPane textEditorPane = getMarkdownEditorPane();
        if (textEditorPane != null) {
            Platform.runLater(() -> {
                if (!closed && !textEditorPane.isEditorClosed()) {
                    textEditorPane.scrollToPixel(0, scrollAmount);
                }
            });
        }
    }

    private HBox createViewButtons() {
        Button codeView = createButton(FontAwesomeSolid.CODE);
        Button splitView = createButton(FontAwesomeSolid.COLUMNS);
        Button previewView = createButton(FontAwesomeBrands.MARKDOWN);

        var switchButtons = new RRHBox(codeView, splitView, previewView);
        switchButtons.setAlignment(Pos.TOP_RIGHT);

        CommandButtons.bind(codeView, MarkdownCommands.CODE,
            () -> CommandContext.withArgument(project, this, this));

        CommandButtons.bind(splitView, MarkdownCommands.SPLIT,
            () -> CommandContext.withArgument(project, this, this));

        CommandButtons.bind(previewView, MarkdownCommands.PREVIEW,
            () -> CommandContext.withArgument(project, this, this));

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

        headingButton.setOnAction(_ -> {
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
        CommandButtons.bind(imageButton, MarkdownCommands.IMAGE,
            () -> CommandContext.withArgument(project, this, this));

        return new RRHBox(headingButton, boldButton, italicButton, quoteButton, codeButton, linkButton,
            unorderedListButton, orderedListButton, taskListButton, horizontalRuleButton, strikethroughButton,
            codeBlockButton, imageButton);
    }

    private CustomMenuItem createMenuItem(int level, int[] headingFontSizes, ContextMenu headingMenu) {
        String labelText = "H" + level;

        var preview = new Label(labelText);
        preview.getStyleClass().add("markdown-preview-heading-label");
        preview.setStyle("-fx-font-size: " + headingFontSizes[level - 1] + "px;");

        var item = new CustomMenuItem(preview, true);

        CommandMenuItems.bind(item, MarkdownCommands.heading(level),
            () -> CommandContext.withArgument(project, this, this));

        return item;
    }

    private Button createButton(Ikon icon) {
        var button = new RRButton("", icon);
        button.setSquare(true);
        button.setRounded(false);
        return button;
    }

    private void setButtonOnAction(Button button, String prefix) {
        CommandButtons.bind(button, MarkdownCommands.insertion(prefix),
            () -> CommandContext.withArgument(project, this, this));
    }

    private void setButtonOnAction(Button button, String prefix, String postfix) {
        CommandButtons.bind(button, MarkdownCommands.insertion(prefix),
            () -> CommandContext.withArgument(project, this, this));
    }

    /**
     * Applies and persists the layout selected by an existing toolbar action.
     *
     * @param layoutType requested layout
     */
    public void applyLayout(MarkdownLayoutType layoutType) {
        switchLayout(layoutType);
        showContent(switch (layoutType) {
            case CODE -> codeView();
            case SPLIT -> splitView();
            case PREVIEW -> previewView();
        }, topRow);
        project.getDataStore().writeJson(MARKDOWN_LAYOUT_LOCATION, new MarkdownLayout(layoutType));
    }

    /**
     * Inserts the syntax used by the existing Markdown formatting toolbar.
     *
     * @param prefix syntax inserted before the caret
     * @param postfix closing syntax, or null for a prefix followed by a space
     */
    public void insertMarkdown(String prefix, String postfix) {
        TextEditorPane editor = editorForInsertion();
        int caret = editor.getCaretPosition();
        editor.insertText(caret, prefix + (postfix == null ? " " : postfix));
        if (postfix != null) {
            editor.moveTo(caret + prefix.length());
        }
        editor.requestFocus();
    }

    private TextEditorPane editorForInsertion() {
        return getMarkdownEditorPane() == null ? codeView() : getMarkdownEditorPane();
    }

    /**
     * Opens the existing image-insertion dialog.
     */
    public void imageDialog() {
        var altTextField = new RRTextField("railroad.markdown.image_dialog.image_alt_text", FontAwesomeSolid.IMAGE);
        var uriTextField = new RRTextField("railroad.markdown.image_dialog.image_uri_prompt", FontAwesomeSolid.LINK);

        altTextField.setPrefColumnCount(42);
        uriTextField.setPrefColumnCount(42);
        altTextField.getStyleClass().add("markdown-image-dialog-field");
        uriTextField.getStyleClass().add("markdown-image-dialog-field");

        TextEditorPane currentEditor = getMarkdownEditorPane();
        if (currentEditor != null && !currentEditor.getSelectedText().isBlank()) {
            altTextField.setText(currentEditor.getSelectedText());
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
                .submitOnEnter(false));
        Platform.runLater(() -> {
            addNodeStyles(dialog);
            dialog.getScene().getRoot().applyCss();
            dialog.getScene().getRoot().layout();
            dialog.sizeToScene();
        });
    }

    private static void addNodeStyles(Stage dialog) {
        Node title = dialog.getScene().lookup(".alert-title");
        if (title != null) {
            title.getStyleClass().add("markdown-image-dialog-title");
        }

        Node header = dialog.getScene().lookup(".alert-header");
        if (header != null) {
            header.getStyleClass().add("markdown-image-dialog-header");
        }

        Node buttons = dialog.getScene().lookup(".alert-buttons");
        if (buttons != null) {
            buttons.getStyleClass().add("markdown-image-dialog-buttons");
        }
    }

    private void createDialogButtons(
        RRButton cancelButton,
        RRButton insertButton,
        RRTextField uriTextField,
        RRTextField altTextField
    ) {
        insertButton.setVariant(ButtonVariant.PRIMARY);
        insertButton.setButtonSize(ButtonSize.LARGE);
        insertButton.getStyleClass().add("markdown-image-dialog-button");
        insertButton.setDefaultButton(true);
        insertButton.disableProperty().bind(Bindings.createBooleanBinding(
            () -> uriTextField.getText().trim().isEmpty(),
            uriTextField.textProperty()));

        cancelButton.setVariant(ButtonVariant.SECONDARY);
        cancelButton.setButtonSize(ButtonSize.LARGE);
        cancelButton.getStyleClass().add("markdown-image-dialog-button");

        cancelButton.setOnAction(_ -> {
            if (insertButton.getScene().getWindow() instanceof Stage stage) {
                stage.close();
            }
        });

        insertButton.setOnAction(_ -> {
            insertMarkdownImage(altTextField.getText(), uriTextField.getText());
            if (insertButton.getScene().getWindow() instanceof Stage stage) {
                stage.close();
            }
        });
    }

    private void insertMarkdownImage(String altText, String uri) {
        if (uri == null || uri.trim().isEmpty())
            return;

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

    /**
     * Stores the selected Markdown editor layout.
     *
     * @param layoutType source, preview, or split layout to display
     */
    public record MarkdownLayout(MarkdownLayoutType layoutType) {
    }

    /**
     * Selects which Markdown source and preview panes are visible.
     */
    public enum MarkdownLayoutType {
        /**
         * Display source and rendered Markdown side by side.
         */
        SPLIT,
        /**
         * Display only the rendered Markdown preview.
         */
        PREVIEW,
        /**
         * Display only the Markdown source editor.
         */
        CODE
    }
}
