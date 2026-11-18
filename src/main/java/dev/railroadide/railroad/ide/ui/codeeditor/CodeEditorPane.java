package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.command.Command;
import dev.railroadide.railroad.command.CommandContext;
import dev.railroadide.railroad.command.CommandMenuItems;
import dev.railroadide.railroad.command.Commands;
import dev.railroadide.railroad.command.EditCommands;
import dev.railroadide.railroad.ide.completion.CompletionItem;
import dev.railroadide.railroad.ide.completion.CompletionProvider;
import dev.railroadide.railroad.ide.completion.CompletionResult;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticsProvider;
import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic;
import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic.TextEditorDiagnostic;
import dev.railroadide.railroad.ide.signature.SignatureHelp;
import dev.railroadide.railroad.ide.signature.SignatureHelpProvider;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.provider.api.NavigationProvider;
import dev.railroadide.railroad.ide.sst.provider.api.UsageQuery;
import dev.railroadide.railroad.ide.sst.request.api.CancellationTokenSource;
import dev.railroadide.railroad.ide.sst.request.api.CancellationToken;
import dev.railroadide.railroad.ide.sst.request.api.Deadline;
import dev.railroadide.railroad.ide.sst.request.api.DeadlineExceededException;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.request.api.RequestPriority;
import dev.railroadide.railroad.ide.sst.request.api.SstScheduler;
import dev.railroadide.railroad.ide.sst.provider.api.ProviderExecutor;
import dev.railroadide.railroad.ide.ui.DiagnosticPane;
import dev.railroadide.railroad.ide.ui.navigation.UsagesPane;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.settings.keybinds.KeybindContexts;
import dev.railroadide.railroad.settings.keybinds.KeybindHandler;
import dev.railroadide.railroad.ui.RRListView;
import dev.railroadide.railroad.ui.id.UIIds;
import io.github.palexdev.mfxresources.fonts.MFXFontIcon;
import io.github.palexdev.mfxresources.fonts.fontawesome.FontAwesomeSolid;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexRange;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
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
import org.reactfx.Subscription;

import dev.railroadide.railroad.ide.diagnostics.DiagnosticSeverity;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Provides a text editor with configurable highlighting, completion, diagnostics, and signature help.
 */
public abstract class CodeEditorPane extends TextEditorPane {
    protected final Project project;
    protected final @Nullable CompletionProvider completionProvider;
    protected final @Nullable DiagnosticsProvider<TextEditorDiagnostic> diagnosticsProvider;
    protected final @Nullable SignatureHelpProvider signatureHelpProvider;
    protected final @Nullable SyntaxHighlightingProvider highlightingProvider;
    protected final @Nullable NavigationProvider navigationProvider;

    protected final SstScheduler.Scope featureRequests = SstScheduler.shared().newScope();
    private static final Duration INTERACTIVE_BUDGET = Duration.ofSeconds(2);
    private static final Duration NAVIGATION_BUDGET = Duration.ofSeconds(15);
    private static final double NAVIGATION_CHOICE_HEIGHT = 36;
    private static final Duration HIGHLIGHT_BUDGET = Duration.ofSeconds(5);
    private static final Duration DIAGNOSTICS_BUDGET = Duration.ofSeconds(30);
    private boolean codeEditorClosed;
    private final List<Subscription> subscriptions = new ArrayList<>();
    private DocumentStore.ChangeSubscription workspaceSubscription;
    private WorkspaceSnapshot observedWorkspace;
    private final AtomicBoolean workspaceRefreshQueued = new AtomicBoolean();

    // region Diagnostics state
    protected static final Duration DIAGNOSTIC_DEBOUNCE = Duration.ofMillis(300);
    private final PauseTransition workspaceDiagnosticsDelay = new PauseTransition(
        javafx.util.Duration.millis(DIAGNOSTIC_DEBOUNCE.toMillis()));

    protected final Map<Integer, DiagnosticSeverity> lineSeverity = new ConcurrentHashMap<>();
    protected final Map<Integer, String> lineDiagnosticMessages = new ConcurrentHashMap<>();
    protected final AtomicInteger diagnosticsGeneration = new AtomicInteger();

    protected volatile List<TextEditorDiagnostic> visibleDiagnostics = List.of();
    protected final Popup diagnosticPopup = new Popup();

    private final CancellationToken providerLifetime;
    private @Nullable CancellationTokenSource activeDiagnosticsCancellation;
    private @Nullable FeatureRequestContext activeDiagnosticsContext;
    // endregion

    // region Syntax Highlighting state
    protected static final Duration HIGHLIGHT_DEBOUNCE = Duration.ofMillis(120);
    protected final AtomicInteger highlightGeneration = new AtomicInteger();

    protected volatile StyleSpans<Collection<String>> lastHighlight = StyleSpans.singleton(Collections.emptyList(), 0);

    private @Nullable CancellationTokenSource activeHighlightCancellation;
    private @Nullable FeatureRequestContext activeHighlightContext;
    // endregion

    // region Completion state
    protected final List<CompletionItem> completionCandidates = new ArrayList<>();
    protected volatile int completionReplacementStart = -1;
    protected final AtomicInteger completionGeneration = new AtomicInteger();
    protected final AtomicReference<Popup> activeCompletionPopup = new AtomicReference<>(null);
    protected final AtomicReference<RRListView<CompletionItem>> activeCompletionList = new AtomicReference<>(null);
    protected volatile ChangeListener<String> completionFilterListener = null;

    private @Nullable CancellationTokenSource activeCompletionCancellation;
    private @Nullable FeatureRequestContext activeCompletionContext;
    // endregion

    // region Signature Help state
    protected final Popup signaturePopup = new Popup();
    protected final TextFlow signatureTextFlow = new TextFlow();
    protected final AtomicInteger signatureGeneration = new AtomicInteger();
    protected final AtomicReference<SignatureHelp> activeSignatureHelp = new AtomicReference<>(null);

    private @Nullable CancellationTokenSource activeSignatureCancellation;
    private @Nullable FeatureRequestContext activeSignatureContext;
    // endregion

    /**
     * Keybind context of code editors, in which navigation shortcuts are dispatched before workspace shortcuts.
     */
    public static final String KEYBIND_CONTEXT = "railroad:code_editor";

    private @Nullable CancellationTokenSource activeNavigationCancellation;
    private @Nullable FeatureRequestContext activeNavigationContext;
    private final Popup navigationPopup = new Popup();
    private @Nullable PendingNavigation activeNavigationChoices;
    private @Nullable String activeNavigationMessage;
    private @Nullable ContextMenu activeContextMenu;

    // region Bracket Highlighting state
    protected static final Map<Character, Character> OPENING_BRACKETS = Map.of('(', ')', '{', '}', '[', ']');
    protected static final Map<Character, Character> CLOSING_BRACKETS = Map.of(')', '(', '}', '{', ']', '[');

    protected int[] bracketHighlightRange;
    // endregion

    // region Style application state
    protected volatile StyleSpans<Collection<String>> lastAppliedStyles = null;
    // endregion

    /**
     * Creates a code editor using the owner's document identity and language services.
     *
     * @param project project whose files and workspace are being displayed
     * @param documentId authoritative logical identity shared with the editor tab
     * @param filePath file to open in the editor
     * @param config language-service configuration for the editor
     * @throws NullPointerException if any argument is {@code null}
     */
    @SuppressWarnings("unchecked")
    protected CodeEditorPane(Project project, DocumentId documentId, Path filePath, CodeEditorConfig config) {
        super(documentId, filePath, config.languageId());
        this.project = Objects.requireNonNull(project, "project");
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(config, "config");
        this.providerLifetime = config.providerLifetime();
        this.completionProvider = config.completionProvider();
        this.diagnosticsProvider = (DiagnosticsProvider<TextEditorDiagnostic>) config.diagnosticsProvider();
        this.signatureHelpProvider = config.signatureHelpProvider();
        this.navigationProvider = config.navigationProvider();
        this.highlightingProvider = config.highlightingProvider() != null
            ? config.highlightingProvider()
            : text -> StyleSpans.singleton(Collections.emptyList(), text.length());

        diagnosticPopup.setAutoHide(true);
        navigationPopup.setAutoHide(true);
        navigationPopup.setHideOnEscape(true);
        navigationPopup.setOnHidden(_ -> {
            activeNavigationChoices = null;
            activeNavigationMessage = null;
        });

        signaturePopup.setAutoHide(false);
        signaturePopup.setAutoFix(true);
        signaturePopup.setHideOnEscape(true);
        signatureTextFlow.getStyleClass().add("signature-help-text");
        var signaturePopupContainer = new StackPane();
        signaturePopupContainer.getStyleClass().add("signature-help-container");
        signaturePopupContainer.getChildren().add(signatureTextFlow);
        signaturePopup.getContent().add(signaturePopupContainer);

        configureGutter();
        workspaceDiagnosticsDelay.setOnFinished(_ -> requestDiagnostics());

        subscriptions.add(plainTextChanges().subscribe(_ -> {
            highlightGeneration.incrementAndGet();
            diagnosticsGeneration.incrementAndGet();
            completionGeneration.incrementAndGet();
            signatureGeneration.incrementAndGet();
            invalidateHighlightRequest();
            invalidateDiagnosticsRequest();
            invalidateCompletionRequest();
            invalidateNavigationRequest();
            visibleDiagnostics = List.of();
            recomputeLineDecorations();
            diagnosticPopup.hide();
            hideAutoComplete();
            hideSignatureHelp();
        }));
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
        installNavigation();
        installContextMenu();
        installBracketHighlighting();
        installDiagnosticPopupHandlers();

    }

    @Override
    public void close() {
        if (codeEditorClosed)
            return;

        super.close();
        if (!isEditorClosed())
            return;

        codeEditorClosed = true;
        workspaceDiagnosticsDelay.stop();
        if (workspaceSubscription != null) {
            workspaceSubscription.close();
            workspaceSubscription = null;
        }
        subscriptions.forEach(Subscription::unsubscribe);
        subscriptions.clear();
        highlightGeneration.incrementAndGet();
        diagnosticsGeneration.incrementAndGet();
        completionGeneration.incrementAndGet();
        signatureGeneration.incrementAndGet();
        invalidateHighlightRequest();
        invalidateDiagnosticsRequest();
        invalidateNavigationRequest();
        hideAutoComplete();
        hideSignatureHelp();
        featureRequests.close();
        diagnosticPopup.hide();
        closeEditorContextMenu();
        Popup completionPopup = activeCompletionPopup.getAndSet(null);
        if (completionPopup != null) {
            completionPopup.hide();
        }
    }

    @Override
    protected void documentStoreAttached() {
        workspaceDiagnosticsDelay.stop();
        if (workspaceSubscription != null) {
            workspaceSubscription.close();
        }
        invalidateHighlightRequest();
        invalidateDiagnosticsRequest();
        invalidateNavigationRequest();
        hideAutoComplete();
        hideSignatureHelp();
        DocumentStore store = getDocumentStore();
        workspaceSubscription = store.onChange(() -> {
            if (workspaceRefreshQueued.compareAndSet(false, true)) {
                Platform.runLater(this::refreshWorkspaceFeatures);
            }
        });
        observedWorkspace = store.capture();
        requestHighlight();
        if (diagnosticsProvider != null) {
            requestDiagnostics();
        }
    }

    private void refreshWorkspaceFeatures() {
        workspaceRefreshQueued.set(false);
        if (codeEditorClosed)
            return;
        WorkspaceSnapshot latest = captureFeatureWorkspace();
        WorkspaceSnapshot previous = observedWorkspace;
        observedWorkspace = latest;
        if (previous == null || latest == null)
            return;
        Map<DocumentId, DocumentSnapshot> oldDependencies = new HashMap<>(previous.documents());
        Map<DocumentId, DocumentSnapshot> newDependencies = new HashMap<>(latest.documents());
        oldDependencies.remove(documentId);
        newDependencies.remove(documentId);
        DocumentSnapshot oldDocument = previous.document(documentId).orElse(null);
        DocumentSnapshot newDocument = latest.document(documentId).orElse(null);
        boolean changedWithoutEditing = oldDocument != newDocument
            && oldDocument instanceof TextDocumentSnapshot oldText
            && newDocument instanceof TextDocumentSnapshot newText && oldText.text().equals(newText.text());
        if (!changedWithoutEditing && previous.configuration().equals(latest.configuration())
            && oldDependencies.equals(newDependencies))
            return;

        diagnosticsGeneration.incrementAndGet();
        completionGeneration.incrementAndGet();
        signatureGeneration.incrementAndGet();
        invalidateDiagnosticsRequest();
        invalidateCompletionRequest();
        invalidateNavigationRequest();
        visibleDiagnostics = List.of();
        recomputeLineDecorations();
        diagnosticPopup.hide();
        hideAutoComplete();
        hideSignatureHelp();
        requestHighlight();
        workspaceDiagnosticsDelay.playFromStart();
    }

    private @Nullable WorkspaceSnapshot captureFeatureWorkspace() {
        DocumentStore store = getDocumentStore();
        return store == null ? null : store.capture();
    }

    private boolean isCurrent(WorkspaceSnapshot workspace) {
        if (codeEditorClosed)
            return false;

        DocumentSnapshot snapshot = workspace.document(documentId).orElse(null);
        if (!(snapshot instanceof TextDocumentSnapshot text) || !getText().equals(text.text()))
            return false;

        DocumentStore store = getDocumentStore();
        if (store == null)
            return false;

        WorkspaceSnapshot latest = store.capture();
        return latest.documents().equals(workspace.documents())
            && latest.configuration().equals(workspace.configuration());
    }

    // region Navigation
    private void invalidateNavigationRequest() {
        featureRequests.cancel("navigation");
        if (activeNavigationCancellation != null) {
            activeNavigationCancellation.cancel();
            activeNavigationCancellation = null;
        }
        activeNavigationContext = null;
        hideNavigationPopup();
    }

    private void installNavigation() {
        if (navigationProvider == null)
            return;

        KeybindHandler.registerCapture(KeybindContexts.of(KEYBIND_CONTEXT), this);
        caretPositionProperty().addListener(
            (observable, previous, current) -> invalidateNavigationRequest());
        addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() != MouseButton.PRIMARY || !event.isShortcutDown() || event.isShiftDown()
                || event.isAltDown() || !event.isStillSincePress())
                return;

            int index = hit(event.getX(), event.getY()).getCharacterIndex().orElse(-1);
            if (index < 0)
                return;

            event.consume();
            moveTo(index);
            goToDefinition();
        });
    }

    /**
     * Reports whether this editor's language provides navigation queries.
     *
     * @return whether definition, implementation and usage commands can run
     */
    public boolean supportsNavigation() {
        return navigationProvider != null && !codeEditorClosed && !providerLifetime.isCancellationRequested();
    }

    /**
     * Navigates to the declaration of the symbol at the caret, offering a choice when it has several.
     * Call on the JavaFX thread.
     */
    public void goToDefinition() {
        requestNavigation(NavigationKind.DEFINITION);
    }

    /**
     * Navigates to an implementation of the type or method at the caret, offering a choice when there are
     * several. Call on the JavaFX thread.
     */
    public void goToImplementation() {
        requestNavigation(NavigationKind.IMPLEMENTATION);
    }

    /**
     * Searches usages of the symbol at the caret and shows them in the workspace's Find Usages tool.
     * Call on the JavaFX thread.
     */
    public void findUsages() {
        usageSearchAtCaret().ifPresent(request -> Services.UI_MANAGER.lookup(UIIds.IDE.IDE)
            .ifPresent(ide -> ide.showUsages(request)));
    }

    /**
     * Captures the inputs of a usage search at the caret from the editor's current document store state,
     * including unsaved changes.
     *
     * @return captured search, or an empty optional when navigation is unsupported or the editor is detached
     */
    public Optional<UsagesPane.Request> usageSearchAtCaret() {
        NavigationProvider provider = navigationProvider;
        DocumentStore store = getDocumentStore();
        if (!supportsNavigation() || provider == null || store == null)
            return Optional.empty();

        WorkspaceSnapshot workspace = store.capture();
        if (!(workspace.document(documentId).orElse(null) instanceof TextDocumentSnapshot snapshot)
            || !snapshot.text().equals(getText()))
            return Optional.empty();

        return Optional.of(new UsagesPane.Request(provider, providerLifetime, store, workspace, snapshot,
            new UsageQuery.AtOffset(getCaretPosition())));
    }

    private void requestNavigation(NavigationKind kind) {
        invalidateNavigationRequest();

        NavigationProvider provider = navigationProvider;
        if (!supportsNavigation() || provider == null)
            return;

        WorkspaceSnapshot workspace = captureFeatureWorkspace();
        if (workspace == null
            || !(workspace.document(documentId).orElse(null) instanceof TextDocumentSnapshot snapshot))
            return;

        int offset = getCaretPosition();
        var cancellation = new CancellationTokenSource(providerLifetime);
        var context = new FeatureRequestContext(workspace, snapshot, cancellation.token(),
            Deadline.after(NAVIGATION_BUDGET));
        activeNavigationCancellation = cancellation;
        activeNavigationContext = context;

        featureRequests.submit("navigation", RequestPriority.NAVIGATION, context,
            cancellation, () -> ProviderExecutor.shared().execute(
                snapshot.languageId() + ":navigation", context, NAVIGATION_BUDGET,
                () -> kind == NavigationKind.DEFINITION
                    ? provider.definitions(context, offset)
                    : provider.implementations(context, offset)))
            .thenAccept(result -> Platform.runLater(() -> {
                if (activeNavigationContext != context)
                    return;

                boolean current = result != null && result.belongsTo(context)
                    && !context.cancellationToken().isCancellationRequested()
                    && !context.deadline().isExpired()
                    && getCaretPosition() == offset
                    && isCurrent(workspace);
                invalidateNavigationRequest();
                if (current) {
                    applyNavigationTargets(kind, result.value(), workspace);
                }
            }))
            .exceptionally(throwable -> {
                Throwable cause = throwable;
                while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                    && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                boolean timedOut = DeadlineExceededException.class.isInstance(cause);
                Platform.runLater(() -> {
                    if (activeNavigationContext == context) {
                        invalidateNavigationRequest();
                        if (timedOut) {
                            showNavigationMessage(L18n.localize("railroad.editor.navigation.timed_out"));
                        }
                    }
                });
                if (timedOut) {
                    Railroad.LOGGER.warn("Timed out resolving {}", kind.name().toLowerCase(Locale.ROOT));
                } else if (!(cause instanceof CancellationException)) {
                    Railroad.LOGGER.error("Failed to resolve {}", kind.name().toLowerCase(Locale.ROOT), cause);
                }
                return null;
            });
    }

    private void applyNavigationTargets(
        NavigationKind kind,
        @Nullable List<Location<?>> targets,
        WorkspaceSnapshot workspace
    ) {
        List<NavigationChoice> choices = new ArrayList<>();
        for (Location<?> target : targets == null ? List.<Location<?>>of() : targets) {
            if (!(target instanceof Location.TextLocation location)
                || !(workspace.document(location.documentId())
                    .orElse(null) instanceof TextDocumentSnapshot destination))
                continue;

            try {
                location.validateAgainst(destination);
            } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
                Railroad.LOGGER.warn("Navigation provider returned an invalid location", exception);
                continue;
            }

            var choice = new NavigationChoice(destination, location);
            if (!choices.contains(choice)) {
                choices.add(choice);
            }
        }

        hideAutoComplete();
        hideSignatureHelp();
        if (choices.isEmpty()) {
            showNavigationMessage(L18n.localize(kind == NavigationKind.DEFINITION
                ? "railroad.editor.navigation.no_definition"
                : "railroad.editor.navigation.no_implementation"));
        } else if (choices.size() == 1) {
            navigate(choices.getFirst(), workspace);
        } else {
            showNavigationChooser(choices, workspace);
        }
    }

    private void navigate(NavigationChoice choice, WorkspaceSnapshot workspace) {
        hideNavigationPopup();
        if (!isCurrent(workspace))
            return;

        Services.EDITOR_TAB_MANAGER.navigateTo(this, choice.snapshot(), choice.location());
    }

    private void showNavigationChooser(List<NavigationChoice> choices, WorkspaceSnapshot workspace) {
        var list = new RRListView<NavigationChoice>();
        list.getStyleClass().add("navigation-chooser");
        list.setCellFactory(_ -> new NavigationChoiceCell());
        list.getItems().setAll(choices);
        list.getSelectionModel().selectFirst();
        int longestLabel = choices.stream().mapToInt(choice -> choice.label().length()).max().orElse(0);
        list.setFixedCellSize(NAVIGATION_CHOICE_HEIGHT);
        list.setPrefSize(Math.clamp(longestLabel * 8.0 + 56, 320, 760),
            Math.min(choices.size(), 10) * NAVIGATION_CHOICE_HEIGHT + 24);
        list.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        list.setOnMouseClicked(_ -> {
            NavigationChoice selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) {
                navigate(selected, workspace);
            }
        });
        list.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                NavigationChoice selected = list.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    navigate(selected, workspace);
                }
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                hideNavigationPopup();
                event.consume();
            }
        });

        showNavigationPopup(list);
        activeNavigationChoices = new PendingNavigation(List.copyOf(choices), workspace);
        list.requestFocus();
    }

    private void showNavigationMessage(String message) {
        var label = new Label(message);
        label.getStyleClass().add("navigation-message");
        showNavigationPopup(label);
        activeNavigationMessage = message;
    }

    private void showNavigationPopup(Node content) {
        hideNavigationPopup();
        navigationPopup.getContent().setAll(content);
        if (getScene() == null || getScene().getWindow() == null || !getScene().getWindow().isShowing())
            return;

        Point2D anchor = getCaretBounds()
            .map(bounds -> new Point2D(bounds.getMinX(), bounds.getMaxY()))
            .orElseGet(() -> localToScreen(0, 0));
        if (anchor != null) {
            navigationPopup.show(this, anchor.getX(), anchor.getY());
        }
    }

    private void hideNavigationPopup() {
        activeNavigationChoices = null;
        activeNavigationMessage = null;
        navigationPopup.hide();
        navigationPopup.getContent().clear();
    }

    /**
     * Returns the targets offered by an open ambiguity chooser.
     *
     * @return offered targets in rank order, or an empty list when no chooser is open
     */
    protected final List<Location.TextLocation> navigationChoices() {
        PendingNavigation pending = activeNavigationChoices;
        return pending == null
            ? List.of()
            : pending.choices().stream().map(NavigationChoice::location).toList();
    }

    /**
     * Navigates to one target of the open ambiguity chooser, as if the user had picked it.
     *
     * @param index zero-based index into {@link #navigationChoices()}
     */
    protected final void chooseNavigationTarget(int index) {
        PendingNavigation pending = activeNavigationChoices;
        if (pending != null && index >= 0 && index < pending.choices().size()) {
            navigate(pending.choices().get(index), pending.workspace());
        }
    }

    /**
     * Returns the message shown after a navigation request found no target.
     *
     * @return visible message, or {@code null} when none is shown
     */
    protected final @Nullable String navigationMessage() {
        return activeNavigationMessage;
    }

    private enum NavigationKind {
        DEFINITION,
        IMPLEMENTATION
    }

    private record NavigationChoice(TextDocumentSnapshot snapshot, Location.TextLocation location) {
        private String label() {
            LineMap lines = snapshot.lineMap();
            int line = location.line();
            String name = snapshot.uri().fileName().orElse(snapshot.uri().toString());
            return name + ":" + (line + 1) + "  "
                + snapshot.text().substring(lines.lineStart(line), lines.lineEnd(line)).strip();
        }
    }

    private record PendingNavigation(List<NavigationChoice> choices, WorkspaceSnapshot workspace) {
    }

    private static final class NavigationChoiceCell extends ListCell<NavigationChoice> {
        @Override
        protected void updateItem(NavigationChoice item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.label());
        }
    }
    // endregion

    // region Context Menu
    private void installContextMenu() {
        addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            if (codeEditorClosed)
                return;

            if (!event.isKeyboardTrigger()) {
                int index = hit(event.getX(), event.getY()).getCharacterIndex().orElse(-1);
                IndexRange selection = getSelection();
                if (index >= 0 && (selection.getLength() == 0 || index < selection.getStart()
                    || index >= selection.getEnd())) {
                    moveTo(index);
                }
            }

            hideAutoComplete();
            hideSignatureHelp();
            closeEditorContextMenu();
            ContextMenu menu = createEditorContextMenu();
            activeContextMenu = menu;
            if (getScene() != null && getScene().getWindow() != null && getScene().getWindow().isShowing()) {
                Point2D anchor = event.isKeyboardTrigger()
                    ? getCaretBounds().map(bounds -> new Point2D(bounds.getMinX(), bounds.getMaxY()))
                        .orElseGet(() -> localToScreen(0, 0))
                    : new Point2D(event.getScreenX(), event.getScreenY());
                menu.show(this, anchor.getX(), anchor.getY());
            }
            event.consume();
        });
    }

    /**
     * Builds the editor's context menu for the current caret: symbol navigation followed by clipboard editing.
     * Items are enabled from their commands' current state.
     *
     * @return new context menu
     */
    protected final ContextMenu createEditorContextMenu() {
        Supplier<CommandContext<CodeEditorPane>> navigation = () -> CommandContext.withArgument(project, this,
            this);
        Supplier<CommandContext<Void>> editing = () -> CommandContext.forProject(project, this);
        var menu = new ContextMenu(
            menuItem(Commands.GO_TO_DEFINITION, navigation),
            menuItem(Commands.GO_TO_IMPLEMENTATION, navigation),
            menuItem(Commands.FIND_USAGES, navigation),
            new SeparatorMenuItem(),
            menuItem(EditCommands.CUT, editing),
            menuItem(EditCommands.COPY, editing),
            menuItem(EditCommands.PASTE, editing));
        menu.getStyleClass().add("code-editor-context-menu");
        return menu;
    }

    /**
     * Returns the context menu opened by the last request, while it is open.
     *
     * @return open context menu, or {@code null}
     */
    protected final @Nullable ContextMenu activeContextMenu() {
        return activeContextMenu;
    }

    private static <T> MenuItem menuItem(Command<T> command, Supplier<CommandContext<T>> context) {
        MenuItem item = CommandMenuItems.create(command, context);
        item.setDisable(!command.canExecute(context.get()));
        return item;
    }

    private void closeEditorContextMenu() {
        ContextMenu menu = activeContextMenu;
        activeContextMenu = null;
        if (menu != null) {
            menu.hide();
        }
    }
    // endregion

    // region Gutter
    private void configureGutter() {
        getGutter().setLineNumberFactory(line -> {
            var label = new Label(String.format("%4d", line));
            label.setTextAlignment(TextAlignment.RIGHT);
            label.getStyleClass().add("editor-line-number");
            return label;
        });
        getGutter().addColumn(new GutterColumn("diagnostics", 12, this::createDiagnosticMarker));
    }

    private @Nullable Node createDiagnosticMarker(int line) {
        DiagnosticSeverity severity = lineSeverity.get(line);
        if (severity == null)
            return null;

        FontAwesomeSolid iconType = switch (severity) {
            case ERROR -> FontAwesomeSolid.CIRCLE_EXCLAMATION;
            case WARNING -> FontAwesomeSolid.TRIANGLE_EXCLAMATION;
            case INFORMATION, HINT -> FontAwesomeSolid.CIRCLE_INFO;
        };

        var icon = new MFXFontIcon(iconType, 12);
        icon.getStyleClass().add("editor-diagnostic-" + severity.name().toLowerCase(Locale.ROOT));
        String tooltipText = lineDiagnosticMessages.getOrDefault(
            line,
            severity.name());
        Tooltip.install(icon, new Tooltip(tooltipText));
        return icon;
    }
    // endregion

    // region Syntax Highlighting

    /**
     * Cancels the JavaFX-thread-owned highlighting request and releases its captured inputs.
     */
    private void invalidateHighlightRequest() {
        featureRequests.cancel("highlighting");
        if (activeHighlightCancellation != null) {
            activeHighlightCancellation.cancel();
            activeHighlightCancellation = null;
        }

        activeHighlightContext = null;
    }

    private void installSyntaxHighlighting() {
        requestHighlight();
        subscriptions.add(multiPlainChanges()
            .successionEnds(HIGHLIGHT_DEBOUNCE)
            .subscribe(_ -> requestHighlight()));
    }

    /**
     * Captures highlighting inputs once the document store is attached and supersedes
     * previous work. The JavaFX callback rechecks identity, activity, generation, and
     * workspace state before applying styles. Workspace changes schedule a replacement
     * even when the document text is unchanged.
     */
    private void requestHighlight() {
        invalidateHighlightRequest();
        if (codeEditorClosed || providerLifetime.isCancellationRequested() || !supportsHighlighting())
            return;

        int generation = highlightGeneration.incrementAndGet();
        WorkspaceSnapshot workspace = captureFeatureWorkspace();
        if (workspace == null
            || !(workspace.document(documentId).orElse(null) instanceof TextDocumentSnapshot snapshot))
            return;

        activeHighlightCancellation = new CancellationTokenSource(providerLifetime);
        var context = new FeatureRequestContext(workspace, snapshot, activeHighlightCancellation.token(),
            Deadline.after(HIGHLIGHT_BUDGET));
        activeHighlightContext = context;

        featureRequests.submit("highlighting", RequestPriority.OPEN_DOCUMENT_ANALYSIS, context,
            activeHighlightCancellation, () -> ProviderExecutor.shared().execute(
                context.documentSnapshot().languageId() + ":highlighting", context, HIGHLIGHT_BUDGET,
                () -> highlightingProvider.compute(context)))
            .thenAccept(result -> Platform.runLater(() -> {
                if (highlightGeneration.get() != generation
                    || activeHighlightContext != context
                    || !result.belongsTo(context)
                    || context.cancellationToken().isCancellationRequested()
                    || context.deadline().isExpired()
                    || !isCurrent(context.workspaceSnapshot()))
                    return;

                applyHighlightIfLatest(generation, result.value());
            }))
            .exceptionally(throwable -> {
                Throwable cause = throwable;
                while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                    && cause.getCause() != null) {
                    cause = cause.getCause();
                }

                if (!(cause instanceof CancellationException)) {
                    Railroad.LOGGER.error("Failed to compute syntax highlighting", cause);
                }
                return null;
            });
    }

    private void applyHighlightIfLatest(int generation, StyleSpans<Collection<String>> spans) {
        if (codeEditorClosed || highlightGeneration.get() != generation)
            return;

        lastHighlight = spans;
        applyEditorStyles();
        restoreBracketHighlight();
    }
    // endregion

    // region Diagnostics

    /**
     * Cancels the previous diagnostics request and releases its owned controls and inputs.
     * Called on the JavaFX thread immediately on invalidation, including during debounce.
     * Generation advancement remains with the calling lifecycle transition.
     */
    private void invalidateDiagnosticsRequest() {
        featureRequests.cancel("diagnostics");
        if (activeDiagnosticsCancellation != null) {
            activeDiagnosticsCancellation.cancel();
            activeDiagnosticsCancellation = null;
        }

        activeDiagnosticsContext = null;
    }

    private void installDiagnostics() {
        if (!supportsDiagnostics())
            return;

        requestDiagnostics();
        subscriptions.add(multiPlainChanges()
            .successionEnds(DIAGNOSTIC_DEBOUNCE)
            .subscribe(changes -> requestDiagnostics()));
    }

    /**
     * Captures and dispatches one diagnostics request, cancelling its predecessor first.
     * Request state is owned by the JavaFX thread. Workers use the captured context;
     * the queued JavaFX publication rechecks request identity, activity, and workspace
     * current-ness before applying the wrapped diagnostics. Expected cancellation is
     * excluded from error reporting while genuine provider failures retain their cause.
     */
    private void requestDiagnostics() {
        invalidateDiagnosticsRequest();

        workspaceDiagnosticsDelay.stop();
        if (codeEditorClosed || providerLifetime.isCancellationRequested() || !supportsDiagnostics())
            return;

        int generation = diagnosticsGeneration.incrementAndGet();

        WorkspaceSnapshot workspace = captureFeatureWorkspace();
        if (workspace == null
            || !(workspace.document(documentId).orElse(null) instanceof TextDocumentSnapshot snapshot))
            return;

        activeDiagnosticsCancellation = new CancellationTokenSource(providerLifetime);
        var context = new FeatureRequestContext(workspace, snapshot, activeDiagnosticsCancellation.token(),
            Deadline.after(DIAGNOSTICS_BUDGET));
        activeDiagnosticsContext = context;

        featureRequests.submit("diagnostics", RequestPriority.VISIBLE_DIAGNOSTICS, context,
            activeDiagnosticsCancellation, () -> ProviderExecutor.shared().execute(
                context.documentSnapshot().languageId() + ":diagnostics", context, DIAGNOSTICS_BUDGET,
                () -> diagnosticsProvider.compute(context)))
            .thenAccept(result -> Platform.runLater(() -> {
                if (activeDiagnosticsContext != context
                    || !result.belongsTo(context)
                    || context.cancellationToken().isCancellationRequested()
                    || context.deadline().isExpired()
                    || !isCurrent(context.workspaceSnapshot()))
                    return;

                applyDiagnosticsIfLatest(generation, result.value(), snapshot);
            }))
            .exceptionally(throwable -> {
                Throwable cause = throwable;
                while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                    && cause.getCause() != null) {
                    cause = cause.getCause();
                }

                if (!(cause instanceof CancellationException)) {
                    Railroad.LOGGER.error("Failed to analyse diagnostics", cause);
                }

                return null;
            });
    }

    private void applyDiagnosticsIfLatest(
        int generation,
        List<TextEditorDiagnostic> diagnostics,
        DocumentSnapshot snapshot
    ) {
        if (diagnosticsGeneration.get() != generation)
            return;

        diagnosticPopup.hide();

        visibleDiagnostics = validatedDiagnostics(diagnostics, snapshot);
        boolean lineDecorationsChanged = recomputeLineDecorations();
        applyEditorStyles();
        restoreBracketHighlight();
        if (lineDecorationsChanged) {
            getGutter().refresh();
        }
    }

    /**
     * Filters diagnostics to those whose locations are valid for the analyzed snapshot.
     *
     * @param diagnostics the diagnostics to validate
     * @param snapshot the document revision used for analysis
     * @return an immutable list containing only valid diagnostics
     */
    public static List<TextEditorDiagnostic> validatedDiagnostics(
        List<TextEditorDiagnostic> diagnostics,
        DocumentSnapshot snapshot
    ) {
        List<TextEditorDiagnostic> valid = new ArrayList<>();
        for (TextEditorDiagnostic diagnostic : diagnostics) {
            try {
                diagnostic.location().validateAgainst(snapshot);
                valid.add(diagnostic);
            } catch (IllegalArgumentException | IndexOutOfBoundsException | NullPointerException exception) {
                Railroad.LOGGER.warn("Discarding diagnostic with an invalid location for snapshot {} version {}",
                    snapshot.id(), snapshot.version(), exception);
            }
        }
        return List.copyOf(valid);
    }

    private void installDiagnosticPopupHandlers() {
        addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN, this::handleMouseOverText);
        addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_END, event -> diagnosticPopup.hide());
        addEventHandler(MouseEvent.MOUSE_MOVED, this::handleMouseMoved);
    }

    private EditorDiagnostic<?> findDiagnosticAt(int index) {
        if (index < 0)
            return null;

        for (EditorDiagnostic<?> diagnostic : visibleDiagnostics) {
            if (diagnostic.location().range().contains(index))
                return diagnostic;
        }

        return null;
    }

    private void showDiagnosticPopup(EditorDiagnostic<?> diagnostic, double screenX, double screenY) {
        diagnosticPopup.getContent().clear();
        diagnosticPopup.getContent().add(new DiagnosticPane(diagnostic, filePath));
        diagnosticPopup.show(this, screenX, screenY);
    }

    private void handleMouseOverText(MouseOverTextEvent event) {
        int index = event.getCharacterIndex();
        EditorDiagnostic<?> diagnostic = findDiagnosticAt(index);
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

    /**
     * Cancels the JavaFX-thread-owned completion request and releases its captured inputs.
     */
    private void invalidateCompletionRequest() {
        featureRequests.cancel("completion");
        if (activeCompletionCancellation != null) {
            activeCompletionCancellation.cancel();
            activeCompletionCancellation = null;
        }

        activeCompletionContext = null;
    }

    private void installCompletion() {
        if (!supportsCompletion())
            return;

        subscriptions.add(plainTextChanges().subscribe(this::showAutocomplete));

        setOnMouseClicked(event -> hideAutoComplete());
        focusedProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue) {
                hideAutoComplete();
            }
        });

        caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            if (completionReplacementStart >= 0 && newPos < completionReplacementStart) {
                hideAutoComplete();
            }
        });

        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                hideAutoComplete();
            }
        });
    }

    /**
     * Dispatches completion for a captured request after invalidating its predecessor.
     * Publication on the JavaFX thread must validate request identity, activity, generation,
     * and current workspace state before interpreting the possibly unsupported payload.
     *
     * @param triggerAt zero-based UTF-16 completion trigger offset in the captured document
     */
    private void triggerCompletion(int triggerAt) {
        invalidateCompletionRequest();

        if (codeEditorClosed || providerLifetime.isCancellationRequested() || !supportsCompletion())
            return;

        WorkspaceSnapshot workspace = captureFeatureWorkspace();
        if (workspace == null || workspace.document(documentId).isEmpty())
            return;

        activeCompletionCancellation = new CancellationTokenSource(providerLifetime);
        var context = new FeatureRequestContext(workspace, workspace.document(documentId).get(),
            activeCompletionCancellation.token(), Deadline.after(INTERACTIVE_BUDGET));
        activeCompletionContext = context;

        int generation = completionGeneration.incrementAndGet();
        featureRequests.submit("completion", RequestPriority.COMPLETION, context,
            activeCompletionCancellation, () -> ProviderExecutor.shared().execute(
                context.documentSnapshot().languageId() + ":completion", context, INTERACTIVE_BUDGET,
                () -> completionProvider.compute(context, triggerAt)))
            .thenAccept(result -> Platform.runLater(() -> {
                if (completionGeneration.get() != generation
                    || activeCompletionContext != context
                    || !result.belongsTo(context)
                    || context.cancellationToken().isCancellationRequested()
                    || context.deadline().isExpired()
                    || !isCurrent(context.workspaceSnapshot()))
                    return;

                CompletionResult value = result.value();
                if (value == null || value.items().isEmpty()) {
                    hideAutoComplete();
                } else {
                    handleCompletionResult(value);
                }
            }))
            .exceptionally(throwable -> {
                Throwable cause = throwable;
                while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                    && cause.getCause() != null) {
                    cause = cause.getCause();
                }

                if (!(cause instanceof CancellationException)) {
                    Railroad.LOGGER.error("Failed to compute completion", cause);
                }

                return null;
            });
    }

    private void handleCompletionResult(CompletionResult result) {
        if (result.items().isEmpty() || result.replacementStart() < 0
            || result.replacementStart() > getCaretPosition()) {
            hideAutoComplete();
            return;
        }

        completionCandidates.clear();
        completionCandidates.addAll(result.items());
        completionReplacementStart = result.replacementStart();
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
        if (codeEditorClosed || providerLifetime.isCancellationRequested()) {
            hideAutoComplete();
            return;
        }
        CompletionItem selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null)
            return;

        int caret = getCaretPosition();
        if (completionReplacementStart < 0 || completionReplacementStart > caret) {
            hideAutoComplete();
            return;
        }
        int start = completionReplacementStart;
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
        if (completionReplacementStart < 0 || completionReplacementStart > text.length()) {
            hideAutoComplete();
            return;
        }

        if (caret < completionReplacementStart) {
            hideAutoComplete();
            return;
        }

        String prefix = text.substring(completionReplacementStart, Math.min(caret, text.length()));
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

    /**
     * Releases popup controls and optionally dismisses the request and its completion state.
     * The non-clearing path is used while rebuilding a popup for an accepted result.
     *
     * @param clearState whether to cancel the request and clear its candidates and trigger
     */
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
            invalidateCompletionRequest();
            completionCandidates.clear();
            completionReplacementStart = -1;
        }
    }

    private void showAutocomplete(PlainTextChange change) {
        if (codeEditorClosed || providerLifetime.isCancellationRequested())
            return;
        String inserted = change.getInserted();
        if (inserted == null || inserted.isEmpty())
            return;

        try {
            if (completionProvider.shouldTrigger(inserted)) {
                int triggerAt = change.getPosition() + inserted.length() - 1;
                triggerCompletion(triggerAt);
            }
        } catch (CancellationException _) {
            // The session can close before its editor notification reaches the UI thread.
        } catch (RuntimeException exception) {
            Railroad.LOGGER.error("Completion trigger policy failed", exception);
        }
    }
    // endregion

    // region Signature Help

    /**
     * Cancels the JavaFX-thread-owned signature request and releases its captured inputs.
     */
    private void invalidateSignatureRequest() {
        featureRequests.cancel("signature");
        if (activeSignatureCancellation != null) {
            activeSignatureCancellation.cancel();
            activeSignatureCancellation = null;
        }

        activeSignatureContext = null;
    }

    private void installSignatureHelp() {
        if (!supportsSignatureHelp())
            return;

        caretPositionProperty().addListener((obs, oldPos, newPos) -> requestSignatureHelp(false));

        subscriptions.add(plainTextChanges()
            .successionEnds(Duration.ofMillis(120))
            .subscribe(change -> requestSignatureHelp(true)));

        focusedProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue) {
                hideSignatureHelp();
            }
        });
    }

    /**
     * Supersedes the previous signature request and captures inputs for the current caret.
     * The JavaFX publication validates identity, activity, generation, and workspace state
     * before applying the possibly unsupported payload. Cancellation is an expected outcome.
     *
     * @param textChanged whether a text edit, rather than caret movement, triggered the request
     */
    private void requestSignatureHelp(boolean textChanged) {
        invalidateSignatureRequest();

        if (codeEditorClosed || providerLifetime.isCancellationRequested() || !supportsSignatureHelp()) {
            hideSignatureHelp();
            return;
        }

        String text = getText();
        int caret = getCaretPosition();
        if (!shouldRequestSignatureHelp(text, caret, textChanged)) {
            hideSignatureHelp();
            return;
        }

        int generation = signatureGeneration.incrementAndGet();
        WorkspaceSnapshot workspace = captureFeatureWorkspace();
        if (workspace == null
            || !(workspace.document(documentId).orElse(null) instanceof TextDocumentSnapshot snapshot))
            return;

        activeSignatureCancellation = new CancellationTokenSource(providerLifetime);
        var context = new FeatureRequestContext(workspace, snapshot, activeSignatureCancellation.token(),
            Deadline.after(INTERACTIVE_BUDGET));
        activeSignatureContext = context;

        featureRequests.submit("signature", RequestPriority.SIGNATURE_HELP, context,
            activeSignatureCancellation, () -> ProviderExecutor.shared().execute(
                context.documentSnapshot().languageId() + ":signature", context, INTERACTIVE_BUDGET,
                () -> signatureHelpProvider.compute(context, caret)))
            .thenAccept(result -> Platform.runLater(() -> {
                if (signatureGeneration.get() != generation
                    || activeSignatureContext != context
                    || !result.belongsTo(context)
                    || context.cancellationToken().isCancellationRequested()
                    || context.deadline().isExpired()
                    || !isCurrent(context.workspaceSnapshot()))
                    return;

                applySignatureHelp(generation, result.value());
            }))
            .exceptionally(throwable -> {
                Throwable cause = throwable;
                while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                    && cause.getCause() != null) {
                    cause = cause.getCause();
                }

                if (!(cause instanceof CancellationException)) {
                    Railroad.LOGGER.error("Failed to compute signature help", cause);
                }

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
        var headerBuilder = new StringBuilder();
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
                (help.varargs() && i == parameterCount - 1 && highlightIndex >= parameterCount - 1
                    && highlightIndex >= 0);

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

    /**
     * Cancels pending signature work and releases the displayed help on the JavaFX thread.
     */
    private void hideSignatureHelp() {
        invalidateSignatureRequest();
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

        List<String> styles = new ArrayList<>(getStyleOfChar(position));
        if (!styles.contains("bracket-highlight")) {
            styles.add("bracket-highlight");
        }

        setStyle(position, position + 1, styles);
    }

    private void removeBracketStyle(int position) {
        if (position < 0 || position >= getLength())
            return;

        List<String> styles = new ArrayList<>(getStyleOfChar(position));
        if (styles.remove("bracket-highlight")) {
            setStyle(position, position + 1, styles);
        }
    }
    // endregion

    // region Styles
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
        List<TextEditorDiagnostic> diagnostics
    ) {
        if (baseSpans == null || diagnostics == null || diagnostics.isEmpty())
            return baseSpans;

        int documentLength = 0;
        for (StyleSpan<Collection<String>> span : baseSpans) {
            documentLength += span.getLength();
        }

        TreeMap<Integer, int[]> events = new TreeMap<>();
        for (TextEditorDiagnostic diagnostic : diagnostics) {
            int start = Math.clamp(diagnostic.location().range().start, 0, documentLength);
            int end = Math.clamp(diagnostic.location().range().end, start, documentLength);
            if (end <= start)
                continue;

            if (diagnostic.kind() == DiagnosticSeverity.INFORMATION || diagnostic.kind() == DiagnosticSeverity.HINT)
                continue;
            boolean error = diagnostic.kind() == DiagnosticSeverity.ERROR;
            registerDiagnosticEvent(events, start, error, 1);
            registerDiagnosticEvent(events, end, error, -1);
        }

        if (events.isEmpty())
            return baseSpans;

        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>(
            baseSpans.getSpanCount() + events.size());
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
        int[] deltas = events.computeIfAbsent(offset, _ -> new int[2]);
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
        Map<Integer, DiagnosticSeverity> updatedSeverity = new LinkedHashMap<>();
        Map<Integer, String> updatedMessages = new LinkedHashMap<>();

        for (TextEditorDiagnostic diagnostic : visibleDiagnostics) {
            int line = diagnostic.location().line() + 1;
            DiagnosticSeverity kind = diagnostic.kind();
            DiagnosticSeverity existing = updatedSeverity.get(line);
            if (existing == null || kind.ordinal() < existing.ordinal()) {
                updatedSeverity.put(line, kind);
                updatedMessages.put(line, diagnostic.message(Locale.getDefault()));
            }
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
