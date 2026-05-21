package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.completion.CompletionItem;
import dev.railroadide.railroad.ide.completion.CompletionProvider;
import dev.railroadide.railroad.ide.completion.CompletionResult;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticsProvider;
import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic;
import dev.railroadide.railroad.ide.signature.SignatureHelp;
import dev.railroadide.railroad.ide.signature.SignatureHelpProvider;
import dev.railroadide.railroad.ide.ui.DiagnosticPane;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRListView;
import dev.railroadide.railroad.utility.ShutdownHooks;
import io.github.palexdev.mfxresources.fonts.MFXFontIcon;
import io.github.palexdev.mfxresources.fonts.fontawesome.FontAwesomeSolid;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.Popup;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxmisc.richtext.model.PlainTextChange;
import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.jspecify.annotations.Nullable;

import javax.tools.Diagnostic;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public abstract class CodeEditorPane extends TextEditorPane {
    protected final Project project;
    protected final @Nullable CompletionProvider completionProvider;
    protected final @Nullable DiagnosticsProvider diagnosticsProvider;
    protected final @Nullable SignatureHelpProvider signatureHelpProvider;
    protected final @Nullable SyntaxHighlightingProvider highlightingProvider;

    protected final ExecutorService worker = Executors.newFixedThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
        namedThreadFactory("railroad-code-editor-worker-%d")
    );

    // region Diagnostics state
    protected static final Duration DIAGNOSTIC_DEBOUNCE = Duration.ofMillis(300);

    protected final Map<Integer, Diagnostic.Kind> lineSeverity = new ConcurrentHashMap<>();
    protected final Map<Integer, String> lineDiagnosticMessages = new ConcurrentHashMap<>();
    protected final AtomicInteger diagnosticsGeneration = new AtomicInteger();

    protected volatile List<EditorDiagnostic> visibleDiagnostics = List.of();
    protected final Popup diagnosticPopup = new Popup();
    // endregion

    // region Syntax Highlighting state
    protected static final Duration HIGHLIGHT_DEBOUNCE = Duration.ofMillis(120);
    protected final AtomicInteger highlightGeneration = new AtomicInteger();

    protected volatile StyleSpans<Collection<String>> lastHighlight = StyleSpans.singleton(Collections.emptyList(), 0);
    // endregion

    // region Completion state
    protected final List<CompletionItem> completionCandidates = new ArrayList<>();
    protected volatile int completionDotIndex = -1;
    protected final AtomicInteger completionGeneration = new AtomicInteger();
    protected final AtomicReference<Popup> activeCompletionPopup = new AtomicReference<>(null);
    protected final AtomicReference<RRListView<CompletionItem>> activeCompletionList = new AtomicReference<>(null);
    protected volatile ChangeListener<String> completionFilterListener = null;
    // endregion

    // region Signature Help state
    protected final Popup signaturePopup = new Popup();
    protected final TextFlow signatureTextFlow = new TextFlow();
    protected final AtomicInteger signatureGeneration = new AtomicInteger();
    protected final AtomicReference<SignatureHelp> activeSignatureHelp = new AtomicReference<>(null);
    // endregion

    // region Bracket Highlighting state
    protected static final Map<Character, Character> OPENING_BRACKETS = Map.of('(', ')', '{', '}', '[', ']');
    protected static final Map<Character, Character> CLOSING_BRACKETS = Map.of(')', '(', '}', '{', ']', '[');

    protected int[] bracketHighlightRange;
    // endregion

    // region Style application state
    protected volatile StyleSpans<Collection<String>> lastAppliedStyles = null;
    // endregion

    protected CodeEditorPane(Project project, Path filePath, CodeEditorConfig config) {
        super(filePath, config.languageId());
        this.project = Objects.requireNonNull(project, "project");
        Objects.requireNonNull(filePath, "filePath");
        config = Objects.requireNonNull(config, "config");
        this.completionProvider = config.completionProvider();
        this.diagnosticsProvider = config.diagnosticsProvider();
        this.signatureHelpProvider = config.signatureHelpProvider();
        this.highlightingProvider = config.highlightingProvider() != null
            ? config.highlightingProvider()
            : text -> StyleSpans.singleton(Collections.emptyList(), text.length());

        diagnosticPopup.setAutoHide(true);

        signaturePopup.setAutoHide(false);
        signaturePopup.setAutoFix(true);
        signaturePopup.setHideOnEscape(true);
        signatureTextFlow.getStyleClass().add("signature-help-text");
        var signaturePopupContainer = new StackPane();
        signaturePopupContainer.getStyleClass().add("signature-help-container");
        signaturePopupContainer.getChildren().add(signatureTextFlow);
        signaturePopup.getContent().add(signaturePopupContainer);

        configureParagraphGraphics();

        installSyntaxHighlighting();
        if (supportsDiagnostics()) {
            installDiagnostics();
        }
        if (supportsCompletion()) {
            installCompletion();
        }
        if (supportsSignatureHelp()) {
            installSignatureHelp();
        }
        installBracketHighlighting();
        installDiagnosticPopupHandlers();

        ShutdownHooks.addHook(worker::shutdownNow);
    }

    // region Paragraph Graphics
    private void configureParagraphGraphics() {
        setParagraphGraphicFactory(this::createParagraphGraphic);
    }

    private Node createParagraphGraphic(int line) {
        var grid = new GridPane();
        grid.setHgap(5);
        grid.getStyleClass().add("ide-code-editor-grid");

        var numberColumn = new ColumnConstraints();
        numberColumn.setHgrow(Priority.ALWAYS);

        var iconColumn = new ColumnConstraints();
        iconColumn.setPrefWidth(12);
        iconColumn.setHgrow(Priority.NEVER);

        grid.getColumnConstraints().addAll(numberColumn, iconColumn);

        var label = new Label(String.format("%4d", line + 1));
        label.setTextAlignment(TextAlignment.RIGHT);
        label.setTextFill(Color.LIGHTGRAY);
        grid.add(label, 0, 0);

        Diagnostic.Kind severity = lineSeverity.get(line + 1);
        if (severity != null) {
            FontAwesomeSolid iconType = severity == Diagnostic.Kind.ERROR ?
                FontAwesomeSolid.CIRCLE_EXCLAMATION :
                FontAwesomeSolid.TRIANGLE_EXCLAMATION;
            Color color = severity == Diagnostic.Kind.ERROR ? Color.RED : Color.YELLOW;

            var icon = new MFXFontIcon(iconType, 12, color);
            grid.add(icon, 1, 0);

            String tooltipText = lineDiagnosticMessages.getOrDefault(
                line + 1,
                severity == Diagnostic.Kind.ERROR ? "Error" : "Warning"
            );
            Tooltip.install(icon, new Tooltip(tooltipText));
        }

        return grid;
    }
    // endregion

    // region Syntax Highlighting
    private void installSyntaxHighlighting() {
        requestHighlight(getText());
        multiPlainChanges()
            .successionEnds(HIGHLIGHT_DEBOUNCE)
            .subscribe(changes -> requestHighlight(getText()));
    }

    private void requestHighlight(String snapshot) {
        if (!supportsHighlighting())
            return;

        int generation = highlightGeneration.incrementAndGet();
        CompletableFuture.supplyAsync(() -> this.highlightingProvider.compute(snapshot), worker)
            .thenAccept(spans -> Platform.runLater(() -> applyHighlightIfLatest(generation, spans)))
            .exceptionally(throwable -> {
                Railroad.LOGGER.error("Failed to compute Syntax highlighting", throwable);
                return null;
            });
    }

    private void applyHighlightIfLatest(int generation, StyleSpans<Collection<String>> spans) {
        if (highlightGeneration.get() != generation)
            return;

        lastHighlight = spans;
        applyEditorStyles();
        restoreBracketHighlight();
    }
    // endregion

    // region Diagnostics
    private void installDiagnostics() {
        if (!supportsDiagnostics())
            return;

        requestDiagnostics(getText());
        multiPlainChanges()
            .successionEnds(DIAGNOSTIC_DEBOUNCE)
            .subscribe(changes -> requestDiagnostics(getText()));
    }

    private void requestDiagnostics(String snapshot) {
        if (!supportsDiagnostics())
            return;

        int generation = diagnosticsGeneration.incrementAndGet();
        CompletableFuture.supplyAsync(() -> diagnosticsProvider.compute(snapshot), worker)
            .thenAccept(result -> Platform.runLater(() -> applyDiagnosticsIfLatest(generation, result)))
            .exceptionally(throwable -> {
                Railroad.LOGGER.error("Failed to analyse diagnostics", throwable);
                return null;
            });
    }

    private void applyDiagnosticsIfLatest(int generation, List<EditorDiagnostic> diagnostics) {
        if (diagnosticsGeneration.get() != generation)
            return;

        diagnosticPopup.hide();

        visibleDiagnostics = diagnostics;
        boolean lineDecorationsChanged = recomputeLineDecorations();
        applyEditorStyles();
        restoreBracketHighlight();
        if (lineDecorationsChanged)
            requestLayout();
    }

    private void installDiagnosticPopupHandlers() {
        addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN, this::handleMouseOverText);
        addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_END, event -> diagnosticPopup.hide());
        addEventHandler(MouseEvent.MOUSE_MOVED, this::handleMouseMoved);
    }

    private EditorDiagnostic findDiagnosticAt(int index) {
        if (index < 0)
            return null;

        for (EditorDiagnostic diagnostic : visibleDiagnostics) {
            if (index >= diagnostic.getStartPosition() && index <= diagnostic.getEndPosition())
                return diagnostic;
        }

        return null;
    }

    private void showDiagnosticPopup(EditorDiagnostic diagnostic, double screenX, double screenY) {
        diagnosticPopup.getContent().clear();
        diagnosticPopup.getContent().add(new DiagnosticPane(diagnostic));
        diagnosticPopup.show(this, screenX, screenY);
    }

    private void handleMouseOverText(MouseOverTextEvent event) {
        int index = event.getCharacterIndex();
        EditorDiagnostic diagnostic = findDiagnosticAt(index);
        if (diagnostic == null)
            return;

        Point2D position = event.getScreenPosition();
        showDiagnosticPopup(diagnostic, position.getX(), position.getY() + 6);
    }

    private void handleMouseMoved(MouseEvent event) {
        if (!diagnosticPopup.isShowing())
            return;

        int index = hit(event.getX(), event.getY())
            .getCharacterIndex()
            .orElse(-1);
        if (index < 0 || findDiagnosticAt(index) == null) {
            diagnosticPopup.hide();
        }
    }
    // endregion

    // region Completion
    private void installCompletion() {
        if (!supportsCompletion())
            return;

        plainTextChanges().subscribe(this::showAutocomplete);

        setOnMouseClicked(event -> hideAutoComplete());
        focusedProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue) {
                hideAutoComplete();
            }
        });

        caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            if (completionDotIndex >= 0 && newPos <= completionDotIndex) {
                hideAutoComplete();
            }
        });

        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                hideAutoComplete();
            }
        });
    }

    private void triggerCompletion(int dotIndex) {
        if (!supportsCompletion())
            return;

        String snapshot = getText();
        int generation = completionGeneration.incrementAndGet();
        CompletableFuture.supplyAsync(() -> completionProvider.compute(snapshot, dotIndex), worker)
            .thenAccept(result -> Platform.runLater(() -> {
                if (completionGeneration.get() != generation)
                    return;

                if (result == null || result.items().isEmpty()) {
                    hideAutoComplete();
                } else {
                    handleCompletionResult(result);
                }
            }))
            .exceptionally(throwable -> {
                Railroad.LOGGER.error("Failed to compute code completion", throwable);
                return null;
            });
    }

    private void handleCompletionResult(CompletionResult result) {
        if (result.items().isEmpty()) {
            hideAutoComplete();
            return;
        }

        completionCandidates.clear();
        completionCandidates.addAll(result.items());
        completionDotIndex = result.dotIndex();
        showAutoComplete();
    }

    private void showAutoComplete() {
        hideAutoComplete(false);

        RRListView<CompletionItem> listView = new RRListView<>();
        listView.setCellFactory(view -> new CompletionItemListCell());
        listView.getItems().setAll(completionCandidates);
        listView.getSelectionModel().selectFirst();
        listView.setOnMouseClicked(event -> completeFromSelection(listView));

        var popup = new Popup();
        popup.setAutoHide(true);
        popup.getContent().add(listView);

        activeCompletionList.set(listView);
        activeCompletionPopup.set(popup);

        completionFilterListener = (obs, oldText, newText) -> filterAutoComplete(newText);
        textProperty().addListener(completionFilterListener);

        Optional<Bounds> caretBounds = getCaretBounds();
        if (caretBounds.isPresent()) {
            Bounds bounds = caretBounds.get();
            popup.show(this, bounds.getMaxX(), bounds.getMaxY());
        } else {
            Point2D screen = localToScreen(0, 0);
            popup.show(this, screen.getX(), screen.getY());
        }
    }

    private void completeFromSelection(RRListView<CompletionItem> listView) {
        CompletionItem selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null)
            return;

        int caret = getCaretPosition();
        int start = Math.min(completionDotIndex + 1, caret);
        String insert = selected.insertText();
        replaceText(start, caret, insert);
        moveTo(start + insert.length());
        hideAutoComplete();
    }

    private void filterAutoComplete(String text) {
        Popup popup = activeCompletionPopup.get();
        RRListView<CompletionItem> listView = activeCompletionList.get();
        if (popup == null || listView == null || !popup.isShowing())
            return;

        int caret = getCaretPosition();
        if (completionDotIndex < 0 || completionDotIndex >= text.length() || text.charAt(completionDotIndex) != '.') {
            hideAutoComplete();
            return;
        }

        if (caret <= completionDotIndex) {
            hideAutoComplete();
            return;
        }

        String prefix = text.substring(completionDotIndex + 1, Math.min(caret, text.length()));
        List<CompletionItem> filtered = completionCandidates.stream()
            .filter(item -> item.insertText().startsWith(prefix))
            .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            hideAutoComplete();
        } else {
            listView.getItems().setAll(filtered);
            listView.getSelectionModel().selectFirst();
        }
    }

    private void hideAutoComplete() {
        hideAutoComplete(true);
    }

    private void hideAutoComplete(boolean clearState) {
        Popup popup = activeCompletionPopup.getAndSet(null);
        if (popup != null) {
            popup.hide();
        }

        activeCompletionList.set(null);

        if (completionFilterListener != null) {
            textProperty().removeListener(completionFilterListener);
            completionFilterListener = null;
        }

        if (clearState) {
            completionCandidates.clear();
            completionDotIndex = -1;
        }
    }

    private void showAutocomplete(PlainTextChange change) {
        String inserted = change.getInserted();
        if (inserted == null || inserted.isEmpty())
            return;

        if (inserted.endsWith(".")) {
            int dotIndex = change.getPosition() + inserted.length() - 1;
            triggerCompletion(dotIndex);
        }
    }
    // endregion

    // region Signature Help
    private void installSignatureHelp() {
        if (!supportsSignatureHelp())
            return;

        caretPositionProperty().addListener((obs, oldPos, newPos) -> requestSignatureHelp(false));

        plainTextChanges()
            .successionEnds(Duration.ofMillis(120))
            .subscribe(change -> requestSignatureHelp(true));

        focusedProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue) {
                hideSignatureHelp();
            }
        });
    }

    private void requestSignatureHelp(boolean textChanged) {
        if (!supportsSignatureHelp()) {
            hideSignatureHelp();
            return;
        }

        String snapshot = getText();
        int caret = getCaretPosition();
        if (!shouldRequestSignatureHelp(snapshot, caret, textChanged)) {
            hideSignatureHelp();
            return;
        }

        int generation = signatureGeneration.incrementAndGet();

        CompletableFuture.supplyAsync(() -> signatureHelpProvider.compute(snapshot, caret), worker)
            .thenAccept(help -> Platform.runLater(() -> applySignatureHelp(generation, help)))
            .exceptionally(throwable -> {
                Railroad.LOGGER.error("Failed to compute signature help", throwable);
                return null;
            });
    }

    private void applySignatureHelp(int generation, SignatureHelp help) {
        if (signatureGeneration.get() != generation)
            return;

        if (help == null) {
            hideSignatureHelp();
            return;
        }

        SignatureHelp previous = activeSignatureHelp.get();
        if (previous != null && previous.equals(help)) {
            positionSignaturePopup();
            return;
        }

        activeSignatureHelp.set(help);
        showSignatureHelp(help);
    }

    private void showSignatureHelp(SignatureHelp help) {
        signatureTextFlow.getChildren().clear();

        String owner = help.ownerQualified().isBlank() ? help.ownerDisplay() : help.ownerQualified();
        StringBuilder headerBuilder = new StringBuilder();
        if (help.constructor()) {
            headerBuilder.append("new ");
            if (!owner.isBlank()) {
                headerBuilder.append(owner);
            }
        } else {
            if (!owner.isBlank()) {
                headerBuilder.append(owner).append(".");
            }

            headerBuilder.append(help.methodName());
        }

        headerBuilder.append("(");

        var header = new Text(headerBuilder.toString());
        signatureTextFlow.getChildren().add(header);

        List<SignatureHelp.ParameterInfo> parameters = help.parameters();
        int parameterCount = parameters.size();
        int highlightIndex = help.activeParameter();

        for (int i = 0; i < parameterCount; i++) {
            SignatureHelp.ParameterInfo parameter = parameters.get(i);
            boolean highlight = highlightIndex == i ||
                (help.varargs() && i == parameterCount - 1 && highlightIndex >= parameterCount - 1 && highlightIndex >= 0);

            String paramLabel = parameter.type() + (parameter.name().isBlank() ? "" : " " + parameter.name());
            var paramText = new Text(paramLabel);
            if (highlight) {
                paramText.getStyleClass().add("signature-param-active");
            } else {
                paramText.getStyleClass().add("signature-param");
            }

            signatureTextFlow.getChildren().add(paramText);

            if (i < parameterCount - 1) {
                signatureTextFlow.getChildren().add(new Text(", "));
            }
        }

        var closing = new Text(")");
        signatureTextFlow.getChildren().add(closing);

        if (!help.constructor()) {
            signatureTextFlow.getChildren().add(new Text(" : " + help.returnType()));
        }

        positionSignaturePopup();
    }

    private void positionSignaturePopup() {
        Optional<Bounds> caretBounds = getCaretBounds();
        double x;
        double y;
        if (caretBounds.isPresent()) {
            Bounds bounds = caretBounds.get();
            x = bounds.getMinX();
            y = bounds.getMaxY() + 6;
        } else {
            Point2D screen = localToScreen(0, 0);
            x = screen.getX();
            y = screen.getY();
        }

        if (!signaturePopup.isShowing()) {
            signaturePopup.show(this, x, y);
        } else {
            signaturePopup.setX(x);
            signaturePopup.setY(y);
        }
    }

    private void hideSignatureHelp() {
        activeSignatureHelp.set(null);
        if (signaturePopup.isShowing()) {
            signaturePopup.hide();
        }
    }

    protected abstract boolean shouldRequestSignatureHelp(String text, int caret, boolean textChanged);
    // endregion

    // region Bracket Highlighting
    private void installBracketHighlighting() {
        caretPositionProperty().addListener(
            (obs, oldPos, newPos) -> updateBracketHighlight(newPos));
    }

    private void updateBracketHighlight(int caretPosition) {
        String text = getText();
        if (text.isEmpty()) {
            clearBracketHighlight();
            return;
        }

        int index = caretPosition - 1;
        boolean lookForward;
        if (index >= 0 && index < text.length() && OPENING_BRACKETS.containsKey(text.charAt(index))) {
            lookForward = true;
        } else if (caretPosition < text.length() && OPENING_BRACKETS.containsKey(text.charAt(caretPosition))) {
            index = caretPosition;
            lookForward = true;
        } else if (index >= 0 && index < text.length() && CLOSING_BRACKETS.containsKey(text.charAt(index))) {
            lookForward = false;
        } else if (caretPosition < text.length() && CLOSING_BRACKETS.containsKey(text.charAt(caretPosition))) {
            index = caretPosition;
            lookForward = false;
        } else {
            clearBracketHighlight();
            return;
        }

        Character bracket = text.charAt(index);
        int match = lookForward
            ? findMatchingForward(text, index, OPENING_BRACKETS.getOrDefault(bracket, bracket))
            : findMatchingBackward(text, index, CLOSING_BRACKETS.getOrDefault(bracket, bracket));

        if (match == -1) {
            clearBracketHighlight();
            return;
        }

        applyBracketHighlight(index, match);
    }

    private int findMatchingForward(String text, int start, char target) {
        char opening = text.charAt(start);
        int balance = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == opening) {
                balance++;
            } else if (c == target) {
                balance--;
                if (balance == 0)
                    return i;
            }
        }

        return -1;
    }

    private int findMatchingBackward(String text, int start, char target) {
        char closing = text.charAt(start);
        int balance = 0;
        for (int i = start; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == closing) {
                balance++;
            } else if (c == target) {
                balance--;
                if (balance == 0)
                    return i;
            }
        }

        return -1;
    }

    private void applyBracketHighlight(int first, int second) {
        clearBracketHighlight();
        bracketHighlightRange = new int[]{first, second};
        addBracketStyle(first);
        addBracketStyle(second);
    }

    private void clearBracketHighlight() {
        if (bracketHighlightRange == null)
            return;

        removeBracketStyle(bracketHighlightRange[0]);
        removeBracketStyle(bracketHighlightRange[1]);
        bracketHighlightRange = null;
    }

    private void restoreBracketHighlight() {
        if (bracketHighlightRange == null)
            return;

        addBracketStyle(bracketHighlightRange[0]);
        addBracketStyle(bracketHighlightRange[1]);
    }

    private void addBracketStyle(int position) {
        if (position < 0 || position >= getLength())
            return;

        List<String> styles = new ArrayList<>(getStyleAtPosition(position));
        if (!styles.contains("bracket-highlight")) {
            styles.add("bracket-highlight");
        }

        setStyle(position, position + 1, styles);
    }

    private void removeBracketStyle(int position) {
        if (position < 0 || position >= getLength())
            return;

        List<String> styles = new ArrayList<>(getStyleAtPosition(position));
        if (styles.remove("bracket-highlight")) {
            setStyle(position, position + 1, styles);
        }
    }
    // endregion

    //region Styles
    private void applyEditorStyles() {
        if (lastHighlight == null)
            return;

        StyleSpans<Collection<String>> styledSpans = mergeDiagnosticStyles(lastHighlight, visibleDiagnostics);
        if (styleSpansEqual(lastAppliedStyles, styledSpans))
            return;

        setStyleSpans(0, styledSpans);
        lastAppliedStyles = styledSpans;
        onEditorStylesApplied();
    }

    private static StyleSpans<Collection<String>> mergeDiagnosticStyles(
        StyleSpans<Collection<String>> baseSpans,
        List<EditorDiagnostic> diagnostics
    ) {
        if (baseSpans == null || diagnostics == null || diagnostics.isEmpty())
            return baseSpans;

        int documentLength = 0;
        for (StyleSpan<Collection<String>> span : baseSpans) {
            documentLength += span.getLength();
        }

        TreeMap<Integer, int[]> events = new TreeMap<>();
        for (EditorDiagnostic diagnostic : diagnostics) {
            int start = Math.clamp((int) diagnostic.getStartPosition(), 0, documentLength);
            int end = Math.clamp((int) diagnostic.getEndPosition(), start, documentLength);
            if (end <= start)
                continue;

            boolean error = diagnostic.getKind() == Diagnostic.Kind.ERROR;
            registerDiagnosticEvent(events, start, error, 1);
            registerDiagnosticEvent(events, end, error, -1);
        }

        if (events.isEmpty())
            return baseSpans;

        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>(baseSpans.getSpanCount() + events.size());
        Iterator<Map.Entry<Integer, int[]>> iterator = events.entrySet().iterator();
        Map.Entry<Integer, int[]> nextEvent = iterator.hasNext() ? iterator.next() : null;
        int currentPosition = 0;
        int activeErrors = 0;
        int activeWarnings = 0;

        for (StyleSpan<Collection<String>> span : baseSpans) {
            Collection<String> style = span.getStyle();
            int remaining = span.getLength();

            while (remaining > 0) {
                while (nextEvent != null && nextEvent.getKey() == currentPosition) {
                    int[] deltas = nextEvent.getValue();
                    activeErrors += deltas[0];
                    activeWarnings += deltas[1];
                    nextEvent = iterator.hasNext() ? iterator.next() : null;
                }

                int nextBoundary = nextEvent == null ? Integer.MAX_VALUE : nextEvent.getKey();
                int chunk = Math.min(remaining, nextBoundary - currentPosition);
                if (chunk <= 0)
                    continue;

                builder.add(mergeStyles(style, activeErrors, activeWarnings), chunk);
                currentPosition += chunk;
                remaining -= chunk;
            }
        }

        return builder.create();
    }

    private static void registerDiagnosticEvent(TreeMap<Integer, int[]> events, int offset, boolean error, int delta) {
        int[] deltas = events.computeIfAbsent(offset, ignored -> new int[2]);
        if (error) {
            deltas[0] += delta;
        } else {
            deltas[1] += delta;
        }
    }

    private static Collection<String> mergeStyles(Collection<String> baseStyles, int activeErrors, int activeWarnings) {
        if (activeErrors <= 0 && activeWarnings <= 0)
            return baseStyles;

        LinkedHashSet<String> merged = new LinkedHashSet<>(baseStyles);
        if (activeErrors > 0) {
            merged.add("error");
        } else if (activeWarnings > 0) {
            merged.add("warning");
        }

        return List.copyOf(merged);
    }

    private static boolean styleSpansEqual(
        StyleSpans<Collection<String>> left,
        StyleSpans<Collection<String>> right
    ) {
        if (left == right)
            return true;

        if (left == null || right == null)
            return false;

        if (left.getSpanCount() != right.getSpanCount())
            return false;

        Iterator<StyleSpan<Collection<String>>> leftIterator = left.iterator();
        Iterator<StyleSpan<Collection<String>>> rightIterator = right.iterator();
        while (leftIterator.hasNext() && rightIterator.hasNext()) {
            StyleSpan<Collection<String>> leftSpan = leftIterator.next();
            StyleSpan<Collection<String>> rightSpan = rightIterator.next();
            if (leftSpan.getLength() != rightSpan.getLength())
                return false;
            if (!Objects.equals(leftSpan.getStyle(), rightSpan.getStyle()))
                return false;
        }

        return !leftIterator.hasNext() && !rightIterator.hasNext();
    }

    private boolean recomputeLineDecorations() {
        Map<Integer, Diagnostic.Kind> updatedSeverity = new LinkedHashMap<>();
        Map<Integer, String> updatedMessages = new LinkedHashMap<>();
        for (EditorDiagnostic diagnostic : visibleDiagnostics) {
            int line = (int) diagnostic.getLineNumber();
            if (line <= 0)
                continue;

            Diagnostic.Kind kind = diagnostic.getKind();
            Diagnostic.Kind effectiveKind = kind == Diagnostic.Kind.ERROR ? Diagnostic.Kind.ERROR : Diagnostic.Kind.WARNING;
            Diagnostic.Kind existing = updatedSeverity.get(line);
            if (existing != Diagnostic.Kind.ERROR) {
                updatedSeverity.put(line, effectiveKind);
            }

            updatedMessages.putIfAbsent(line, diagnostic.getMessage(Locale.getDefault()));
        }

        boolean changed = !lineSeverity.equals(updatedSeverity) || !lineDiagnosticMessages.equals(updatedMessages);
        if (changed) {
            lineSeverity.clear();
            lineSeverity.putAll(updatedSeverity);
            lineDiagnosticMessages.clear();
            lineDiagnosticMessages.putAll(updatedMessages);
        }

        return changed;
    }
    // endregion

    protected boolean supportsDiagnostics() {
        return diagnosticsProvider != null;
    }

    protected boolean supportsHighlighting() {
        return highlightingProvider != null;
    }

    protected boolean supportsCompletion() {
        return completionProvider != null;
    }

    protected boolean supportsSignatureHelp() {
        return signatureHelpProvider != null;
    }

    protected void onEditorStylesApplied() {
    }

    protected final void reapplyEditorStyles() {
        lastAppliedStyles = null;
        applyEditorStyles();
        restoreBracketHighlight();
    }

    private static final class CompletionItemListCell extends ListCell<CompletionItem> {
        @Override
        protected void updateItem(CompletionItem item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.displayText());
        }
    }
}
