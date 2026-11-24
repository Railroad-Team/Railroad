package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.command.*;
import dev.railroadide.railroad.ide.completion.CompletionItem;
import dev.railroadide.railroad.ide.completion.CompletionProvider;
import dev.railroadide.railroad.ide.completion.CompletionResult;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticSeverity;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticsProvider;
import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic;
import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic.TextEditorDiagnostic;
import dev.railroadide.railroad.ide.signature.SignatureHelp;
import dev.railroadide.railroad.ide.signature.SignatureHelpProvider;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.provider.api.*;
import dev.railroadide.railroad.ide.sst.request.api.*;
import dev.railroadide.railroad.ide.sst.semantic.api.DefinitionTarget;
import dev.railroadide.railroad.ide.ui.DiagnosticPane;
import dev.railroadide.railroad.ide.ui.codeaction.CodeActionController;
import dev.railroadide.railroad.ide.ui.codeaction.CodeActionMenu;
import dev.railroadide.railroad.ide.ui.navigation.UsagesPane;
import dev.railroadide.railroad.ide.ui.refactor.RenameDialog;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.settings.ParameterPopupPosition;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.settings.keybinds.KeybindContexts;
import dev.railroadide.railroad.settings.keybinds.KeybindHandler;
import dev.railroadide.railroad.ui.RRListView;
import dev.railroadide.railroad.ui.id.UIIds;
import io.github.palexdev.mfxresources.fonts.MFXFontIcon;
import io.github.palexdev.mfxresources.fonts.fontawesome.FontAwesomeSolid;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.Popup;
import javafx.stage.PopupWindow;
import javafx.stage.Screen;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxmisc.richtext.model.PlainTextChange;
import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.jspecify.annotations.Nullable;
import org.reactfx.Subscription;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
    protected final @Nullable RenameProvider renameProvider;
    protected final @Nullable CodeActionProvider codeActionProvider;

    protected final SstScheduler.Scope featureRequests = SstScheduler.shared().newScope();
    private static final Duration INTERACTIVE_BUDGET = Duration.ofSeconds(2);
    private static final Duration COMPLETION_BUDGET = Duration.ofSeconds(8);
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
    private final PauseTransition workspaceFeaturesDelay = new PauseTransition(
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
    private int completionTriggerOffset = -1;
    private int completionPrefixEnd = -1;

    private @Nullable CancellationTokenSource activeCompletionCancellation;
    private @Nullable FeatureRequestContext activeCompletionContext;
    // endregion

    // region Signature Help state
    protected final Popup signaturePopup = new Popup();
    protected final VBox signatureRows = new VBox(3);
    private final StackPane signaturePopupContainer = new StackPane();
    private final ScrollPane signatureScroll = new ScrollPane();
    protected final AtomicInteger signatureGeneration = new AtomicInteger();
    protected final AtomicReference<List<SignatureHelp>> activeSignatureHelp = new AtomicReference<>(List.of());

    private @Nullable CancellationTokenSource activeSignatureCancellation;
    private @Nullable FeatureRequestContext activeSignatureContext;
    private boolean manualSignatureHelp;
    private boolean signatureEditPending;
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
    private @Nullable CancellationTokenSource activeRenameCancellation;
    private @Nullable FeatureRequestContext activeRenameContext;
    private @Nullable RenameDialog activeRenameDialog;
    private static final Duration CODE_ACTION_BULB_DELAY = Duration.ofMillis(250);
    private final @Nullable CodeActionController codeActions;
    private final PauseTransition codeActionBulbDelay = new PauseTransition(
        javafx.util.Duration.millis(CODE_ACTION_BULB_DELAY.toMillis()));
    private @Nullable CodeActionMenu activeCodeActionMenu;
    private @Nullable Object pendingCodeActionMenu;
    private int codeActionBulbLine = -1;

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
        this.renameProvider = config.renameProvider();
        this.codeActionProvider = config.codeActionProvider();
        this.codeActions = codeActionProvider == null
            ? null
            : new CodeActionController(codeActionProvider, providerLifetime, this::getDocumentStore,
                Services.IDE_STATE::getWorkspaceEdits);
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
        signaturePopup.setOnHidden(_ -> resetSignatureHelp());
        signaturePopupContainer.getStyleClass().add("signature-help-container");
        signatureScroll.getStyleClass().add("signature-help-scroll");
        signatureScroll.setFitToWidth(true);
        signatureScroll.setFocusTraversable(false);
        signatureScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        signatureScroll.setPrefViewportHeight(180);
        signaturePopup.getContent().add(signaturePopupContainer);

        configureGutter();
        workspaceFeaturesDelay.setOnFinished(_ -> {
            requestHighlight();
            requestDiagnostics();
        });

        subscriptions.add(plainTextChanges().subscribe(_ -> {
            highlightGeneration.incrementAndGet();
            diagnosticsGeneration.incrementAndGet();
            signatureGeneration.incrementAndGet();
            invalidateHighlightRequest();
            invalidateDiagnosticsRequest();
            invalidateNavigationRequest();
            invalidateRenameRequest();
            invalidateCodeActions();
            visibleDiagnostics = List.of();
            recomputeLineDecorations();
            diagnosticPopup.hide();
            signatureEditPending = true;
            invalidateSignatureRequest();
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
        if (navigationProvider != null || renameProvider != null || codeActions != null
            || signatureHelpProvider != null) {
            KeybindHandler.registerCapture(KeybindContexts.of(KEYBIND_CONTEXT), this);
        }
        installNavigation();
        installRename();
        installCodeActions();
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
        workspaceFeaturesDelay.stop();
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
        invalidateRenameRequest();
        if (activeRenameDialog != null) {
            activeRenameDialog.close();
            activeRenameDialog = null;
        }
        invalidateCodeActions();
        if (codeActions != null) {
            codeActions.close();
        }
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
        workspaceFeaturesDelay.stop();
        if (workspaceSubscription != null) {
            workspaceSubscription.close();
        }
        invalidateHighlightRequest();
        invalidateDiagnosticsRequest();
        invalidateNavigationRequest();
        invalidateCodeActions();
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
        invalidateCodeActions();
        visibleDiagnostics = List.of();
        recomputeLineDecorations();
        diagnosticPopup.hide();
        hideAutoComplete();
        hideSignatureHelp();
        workspaceFeaturesDelay.playFromStart();
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

        int offset = getCaretPosition();
        IndexRange selection = getSelection();
        if (selection.getLength() > 0 && offset == selection.getEnd()) {
            offset--;
        }
        return Optional.of(new UsagesPane.Request(provider, providerLifetime, store, workspace, snapshot,
            new UsageQuery.AtOffset(offset)));
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
        @Nullable List<DefinitionTarget> targets,
        WorkspaceSnapshot workspace
    ) {
        List<NavigationChoice> choices = new ArrayList<>();
        for (DefinitionTarget target : targets == null ? List.<DefinitionTarget>of() : targets) {
            if (target == null)
                continue;

            Location.TextLocation location = target.location();
            DocumentSnapshot captured = workspace.document(location.documentId()).orElse(null);
            // Read-only views outside the capture, such as library sources, are opened from their file.
            TextDocumentSnapshot destination = captured == null && target.snapshot().uri().isFile()
                ? target.snapshot()
                : captured instanceof TextDocumentSnapshot text ? text : null;
            if (destination == null)
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

    // region Rename
    private void installRename() {
        if (renameProvider == null)
            return;

        caretPositionProperty().addListener((observable, previous, current) -> invalidateRenameRequest());
    }

    private void invalidateRenameRequest() {
        featureRequests.cancel("rename");
        if (activeRenameCancellation != null) {
            activeRenameCancellation.cancel();
            activeRenameCancellation = null;
        }
        activeRenameContext = null;
    }

    /**
     * Reports whether the symbol at the caret can be renamed from this editor.
     *
     * @return whether this editor is editable and its language provides semantic renames
     */
    public boolean supportsRename() {
        return renameProvider != null && !codeEditorClosed && !providerLifetime.isCancellationRequested()
            && isEditable();
    }

    /**
     * Checks the symbol at the caret and, when it can be renamed, opens the rename dialog with a preview of every
     * change; otherwise explains why it cannot be renamed. Call on the JavaFX thread.
     */
    public void rename() {
        invalidateRenameRequest();
        hideNavigationPopup();

        RenameProvider provider = renameProvider;
        DocumentStore store = getDocumentStore();
        if (!supportsRename() || provider == null || store == null)
            return;

        WorkspaceSnapshot workspace = store.capture();
        if (!(workspace.document(documentId).orElse(null) instanceof TextDocumentSnapshot snapshot)
            || !snapshot.text().equals(getText()))
            return;

        int offset = getCaretPosition();
        var cancellation = new CancellationTokenSource(providerLifetime);
        var context = new FeatureRequestContext(workspace, snapshot, cancellation.token(),
            Deadline.after(NAVIGATION_BUDGET));
        activeRenameCancellation = cancellation;
        activeRenameContext = context;

        featureRequests.submit("rename", RequestPriority.NAVIGATION, context, cancellation,
            () -> ProviderExecutor.shared().execute(snapshot.languageId() + ":rename", context, NAVIGATION_BUDGET,
                () -> provider.prepareRename(context, offset)))
            .thenAccept(result -> Platform.runLater(() -> {
                if (activeRenameContext != context)
                    return;

                boolean current = result != null && result.belongsTo(context)
                    && !context.cancellationToken().isCancellationRequested()
                    && getCaretPosition() == offset
                    && isCurrent(workspace);
                invalidateRenameRequest();
                if (!current)
                    return;
                switch (result.value()) {
                    case RenamePreparation.Rejected rejected -> showNavigationMessage(
                        L18n.localize("railroad.editor.rename.rejected", rejected.reason()));
                    case RenamePreparation.Renameable symbol -> openRenameDialog(
                        new RenameDialog.Request(provider, providerLifetime, store, snapshot, offset, symbol,
                            Services.IDE_STATE::getWorkspaceEdits));
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
                    if (activeRenameContext == context) {
                        invalidateRenameRequest();
                        if (timedOut) {
                            showNavigationMessage(L18n.localize("railroad.editor.navigation.timed_out"));
                        }
                    }
                });
                if (!(cause instanceof CancellationException)) {
                    Railroad.LOGGER.error("Failed to prepare rename", cause);
                }
                return null;
            });
    }

    private void openRenameDialog(RenameDialog.Request request) {
        if (activeRenameDialog != null) {
            activeRenameDialog.close();
        }
        var dialog = new RenameDialog(request);
        activeRenameDialog = dialog;
        dialog.show(getScene() == null ? null : getScene().getWindow());
    }

    /**
     * Returns the rename dialog opened by the last rename request, while it is open.
     *
     * @return open rename dialog, or {@code null}
     */
    protected final @Nullable RenameDialog activeRenameDialog() {
        RenameDialog dialog = activeRenameDialog;
        return dialog == null || dialog.isClosed() ? null : dialog;
    }
    // endregion

    // region Code Actions
    private void installCodeActions() {
        if (codeActions == null)
            return;

        codeActionBulbDelay.setOnFinished(_ -> refreshCodeActionBulb());
        caretPositionProperty().addListener((observable, previous, current) -> {
            closeCodeActionMenu();
            scheduleCodeActionBulb();
        });
    }

    private void invalidateCodeActions() {
        codeActionBulbDelay.stop();
        pendingCodeActionMenu = null;
        if (codeActions != null) {
            codeActions.cancel();
        }
        closeCodeActionMenu();
        setCodeActionBulbLine(-1);
    }

    private void closeCodeActionMenu() {
        CodeActionMenu menu = activeCodeActionMenu;
        activeCodeActionMenu = null;
        if (menu != null) {
            menu.close();
        }
    }

    private void scheduleCodeActionBulb() {
        if (codeActions == null || codeEditorClosed)
            return;
        if (codeActionBulbLine >= 0 && codeActionBulbLine != getCurrentParagraph() + 1) {
            setCodeActionBulbLine(-1);
        }
        codeActionBulbDelay.playFromStart();
    }

    private void setCodeActionBulbLine(int line) {
        if (codeActionBulbLine != line) {
            codeActionBulbLine = line;
            getGutter().refresh();
        }
    }

    private void refreshCodeActionBulb() {
        if (!supportsCodeActions()) {
            setCodeActionBulbLine(-1);
            return;
        }
        if (activeCodeActionMenu() != null || pendingCodeActionMenu != null)
            return;
        discoverCodeActions(RequestPriority.OPEN_DOCUMENT_ANALYSIS, _ -> {
        }, () -> {
        });
    }

    /**
     * Reports whether this editor can offer code actions for its language.
     *
     * @return whether the editor is open, editable and its language provides code actions
     */
    public boolean supportsCodeActions() {
        return codeActions != null && !codeEditorClosed && !providerLifetime.isCancellationRequested()
            && isEditable();
    }

    /**
     * Discovers the code actions at the caret or selection and shows them in a keyboard-driven menu, or explains
     * that none are available. Call on the JavaFX thread.
     */
    public void showCodeActions() {
        hideNavigationPopup();
        closeCodeActionMenu();
        if (!supportsCodeActions())
            return;
        codeActionBulbDelay.stop();
        var request = new Object();
        pendingCodeActionMenu = request;
        discoverCodeActions(RequestPriority.NAVIGATION, discovery -> {
            if (discovery.actions().isEmpty()) {
                showNavigationMessage(L18n.localize("railroad.code_actions.none"));
                return;
            }
            hideAutoComplete();
            hideSignatureHelp();
            var menu = new CodeActionMenu(Objects.requireNonNull(codeActions), discovery, this::requestFocus);
            activeCodeActionMenu = menu;
            Point2D anchor = getCaretBounds()
                .map(bounds -> new Point2D(bounds.getMinX(), bounds.getMaxY()))
                .orElseGet(() -> localToScreen(0, 0));
            menu.show(this, anchor == null ? 0 : anchor.getX(), anchor == null ? 0 : anchor.getY());
        }, () -> {
            if (pendingCodeActionMenu == request) {
                pendingCodeActionMenu = null;
            }
        });
    }

    private void discoverCodeActions(
        RequestPriority priority,
        Consumer<CodeActionController.Discovery> shown,
        Runnable finished
    ) {
        CodeActionController controller = codeActions;
        WorkspaceSnapshot workspace = captureFeatureWorkspace();
        if (controller == null || workspace == null
            || !(workspace.document(documentId).orElse(null) instanceof TextDocumentSnapshot snapshot)
            || !snapshot.text().equals(getText())) {
            finished.run();
            return;
        }

        IndexRange selection = getSelection();
        int caret = getCaretPosition();
        controller.discover(workspace, snapshot, codeActionRequest(snapshot, selection), priority)
            .whenComplete((discovery, failure) -> {
                finished.run();
                if (failure != null) {
                    Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                        ? failure.getCause()
                        : failure;
                    if (cause instanceof DeadlineExceededException) {
                        if (priority == RequestPriority.NAVIGATION) {
                            showNavigationMessage(L18n.localize("railroad.code_actions.timed_out"));
                        }
                    } else if (!(cause instanceof CancellationException)) {
                        Railroad.LOGGER.error("Failed to discover code actions", cause);
                    }
                    return;
                }
                if (getCaretPosition() != caret || !getSelection().equals(selection) || !isCurrent(workspace))
                    return;
                setCodeActionBulbLine(discovery.hasAvailableActions()
                    ? snapshot.lineMap().positionAt(caret).line() + 1
                    : -1);
                shown.accept(discovery);
            });
    }

    private CodeActionRequest codeActionRequest(TextDocumentSnapshot snapshot, IndexRange selection) {
        LineMap lines = snapshot.lineMap();
        int start = lines.lineStart(lines.positionAt(selection.getStart()).line());
        int end = lines.lineEnd(lines.positionAt(selection.getEnd()).line());
        List<TextEditorDiagnostic> nearby = visibleDiagnostics.stream()
            .filter(diagnostic -> diagnostic.location().range().start <= end
                && diagnostic.location().range().end >= start)
            .toList();
        return new CodeActionRequest(new TextRange(selection.getStart(), selection.getEnd()), nearby);
    }

    /**
     * Returns the code action menu opened by the last request, while it is open.
     *
     * @return open menu, or {@code null}
     */
    public final @Nullable CodeActionMenu activeCodeActionMenu() {
        CodeActionMenu menu = activeCodeActionMenu;
        return menu == null || menu.isClosed() ? null : menu;
    }

    /**
     * Returns the one-based line showing the code action lightbulb.
     *
     * @return line with available actions at the caret, or {@code -1} when no lightbulb is shown
     */
    public final int codeActionBulbLine() {
        return codeActionBulbLine;
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
     * Builds the editor's context menu for the current caret: symbol navigation, code actions, rename and clipboard
     * editing.
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
            menuItem(Commands.SHOW_CODE_ACTIONS, navigation),
            menuItem(Commands.RENAME, navigation),
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
        getGutter().addColumn(new GutterColumn("code-actions", 14, this::createCodeActionBulb, (line, event) -> {
            if (line == codeActionBulbLine) {
                event.consume();
                showCodeActions();
            }
        }));
    }

    private @Nullable Node createCodeActionBulb(int line) {
        if (line != codeActionBulbLine)
            return null;
        var icon = new MFXFontIcon(FontAwesomeSolid.LIGHTBULB, 12);
        icon.getStyleClass().add("editor-code-action-bulb");
        Tooltip.install(icon, new Tooltip(L18n.localize("railroad.code_actions.bulb")));
        return icon;
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
     * editor text before applying styles. Styles for unchanged text are applied even if
     * other workspace state changed meanwhile, so highlighting is not starved while a
     * project loads; workspace changes still schedule a debounced replacement.
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

        featureRequests.submit("highlighting", RequestPriority.SYNTAX_HIGHLIGHTING, context,
            activeHighlightCancellation, () -> ProviderExecutor.shared().execute(
                context.documentSnapshot().languageId() + ":highlighting", context, HIGHLIGHT_BUDGET,
                () -> highlightingProvider.compute(context)))
            .thenAccept(result -> Platform.runLater(() -> {
                if (highlightGeneration.get() != generation
                    || activeHighlightContext != context
                    || !result.belongsTo(context)
                    || context.cancellationToken().isCancellationRequested()
                    || context.deadline().isExpired()
                    || !getText().equals(snapshot.text()))
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

        workspaceFeaturesDelay.stop();
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
        scheduleCodeActionBulb();
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
            if (completionTriggerOffset >= 0 && newPos != completionPrefixEnd) {
                Platform.runLater(() -> {
                    if (completionTriggerOffset >= 0 && getCaretPosition() != completionPrefixEnd) {
                        hideAutoComplete();
                    }
                });
            }
        });

        addEventFilter(KeyEvent.KEY_PRESSED, this::handleCompletionKey);
    }

    private void handleCompletionKey(KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE) {
            hideAutoComplete();
            return;
        }
        if (event.getCode() == KeyCode.SPACE && event.isControlDown() && !event.isAltDown() && !event.isShiftDown()) {
            requestCompletionAtCaret();
            event.consume();
            return;
        }
        var list = activeCompletionList.get();
        if (list == null || !activeCompletionPopup.get().isShowing() || event.isShortcutDown()
            || event.isAltDown() || event.isShiftDown())
            return;
        switch (event.getCode()) {
            case DOWN -> list.getSelectionModel().selectNext();
            case UP -> list.getSelectionModel().selectPrevious();
            case ENTER -> completeFromSelection(list);
            default -> {
                return;
            }
        }
        list.scrollTo(list.getSelectionModel().getSelectedIndex());
        event.consume();
    }

    private void requestCompletionAtCaret() {
        if (codeEditorClosed || providerLifetime.isCancellationRequested())
            return;
        int caret = getCaretPosition();
        String text = getText();
        try {
            for (int offset = caret - 1; offset >= 0; offset--) {
                if (completionProvider.shouldTrigger(text.substring(offset, offset + 1))) {
                    hideAutoComplete();
                    triggerCompletion(offset, caret);
                    return;
                }
                if (!Character.isJavaIdentifierPart(text.charAt(offset)))
                    return;
            }
        } catch (CancellationException _) {
            // The session may close during the trigger check.
        } catch (RuntimeException exception) {
            Railroad.LOGGER.error("Completion trigger policy failed", exception);
        }
    }

    /**
     * Dispatches completion for a captured request after invalidating its predecessor.
     * Publication on the JavaFX thread must validate request identity, activity, generation,
     * and current workspace state before interpreting the possibly unsupported payload.
     *
     * @param triggerAt zero-based UTF-16 completion trigger offset in the captured document
     */
    private void triggerCompletion(int triggerAt) {
        triggerCompletion(triggerAt, triggerAt + 1);
    }

    private void triggerCompletion(int triggerAt, int prefixEnd) {
        invalidateCompletionRequest();

        if (codeEditorClosed || providerLifetime.isCancellationRequested() || !supportsCompletion())
            return;

        WorkspaceSnapshot workspace = captureFeatureWorkspace();
        if (workspace == null || workspace.document(documentId).isEmpty())
            return;

        activeCompletionCancellation = new CancellationTokenSource(providerLifetime);
        var context = new FeatureRequestContext(workspace, workspace.document(documentId).get(),
            activeCompletionCancellation.token(), Deadline.after(COMPLETION_BUDGET));
        activeCompletionContext = context;
        completionTriggerOffset = triggerAt;
        completionPrefixEnd = prefixEnd;

        int generation = completionGeneration.incrementAndGet();
        featureRequests.submit("completion", RequestPriority.COMPLETION, context,
            activeCompletionCancellation, () -> ProviderExecutor.shared().execute(
                context.documentSnapshot().languageId() + ":completion", context, COMPLETION_BUDGET,
                () -> completionProvider.compute(context, triggerAt)))
            .thenAccept(result -> Platform.runLater(() -> {
                if (completionGeneration.get() != generation
                    || activeCompletionContext != context
                    || !result.belongsTo(context)
                    || context.cancellationToken().isCancellationRequested()
                    || context.deadline().isExpired() || getCaretPosition() != completionPrefixEnd
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
        filterAutoComplete();
    }

    private void showAutoComplete(List<CompletionItem> items) {
        RRListView<CompletionItem> listView = new RRListView<>();
        var content = new CompletionPopupContent(listView);
        listView.getItems().setAll(items);
        listView.getSelectionModel().selectFirst();
        listView.setOnMouseClicked(event -> completeFromSelection(listView));

        var popup = new Popup();
        popup.setAutoHide(true);
        popup.addEventFilter(KeyEvent.KEY_PRESSED, this::handleCompletionKey);
        popup.setOnHidden(_ -> {
            if (activeCompletionPopup.get() == popup) {
                hideAutoComplete();
            }
        });
        popup.getContent().add(content);

        activeCompletionList.set(listView);
        activeCompletionPopup.set(popup);

        Optional<Bounds> caretBounds = getCaretBounds();
        if (caretBounds.isPresent()) {
            Bounds bounds = caretBounds.get();
            double x = bounds.getMinX();
            double below = bounds.getMaxY() + 3;
            popup.show(this, x, below);
            var screen = Screen.getScreensForRectangle(bounds.getMinX(), bounds.getMinY(),
                Math.max(1, bounds.getWidth()), bounds.getHeight()).stream().findFirst().orElse(Screen.getPrimary());
            if (below + popup.getHeight() > screen.getVisualBounds().getMaxY()
                && bounds.getMinY() - popup.getHeight() - 3 >= screen.getVisualBounds().getMinY()) {
                popup.setAnchorLocation(PopupWindow.AnchorLocation.WINDOW_BOTTOM_LEFT);
                popup.setAnchorY(bounds.getMinY() - 3);
            }
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
        if (completionReplacementStart < 0 || completionReplacementStart > caret || caret != completionPrefixEnd
            || getSelection().getLength() != 0) {
            hideAutoComplete();
            return;
        }
        int start = completionReplacementStart;
        hideAutoComplete();
        if (selected.kind() == CompletionItem.Kind.METHOD) {
            insertMethodCompletion(selected, start, caret);
        } else {
            replaceText(start, caret, selected.insertText());
            moveTo(start + selected.insertText().length());
        }
        hideAutoComplete();
    }

    private void insertMethodCompletion(CompletionItem method, int start, int caret) {
        String line = getParagraph(getCurrentParagraph()).getText();
        int column = getCaretColumn();
        int nameEnd = column;
        while (nameEnd < line.length() && Character.isJavaIdentifierPart(line.codePointAt(nameEnd))) {
            nameEnd += Character.charCount(line.codePointAt(nameEnd));
        }

        int replaceEnd = caret + (nameEnd - column);
        int open = skipHorizontalWhitespace(line, nameEnd);
        String name = method.insertText();
        if (line.startsWith("(", open)) {
            int inside = open + 1;
            int close = skipHorizontalWhitespace(line, inside);
            int target = !method.hasParameters() && line.startsWith(")", close) ? close + 1 : inside;
            replaceText(start, replaceEnd, name);
            moveTo(start + name.length() + (target - nameEnd));
        } else {
            replaceText(start, replaceEnd, name + "()");
            moveTo(start + name.length() + (method.hasParameters() ? 1 : 2));
        }
    }

    private static int skipHorizontalWhitespace(String text, int offset) {
        while (offset < text.length() && (text.charAt(offset) == ' ' || text.charAt(offset) == '\t')) {
            offset++;
        }
        return offset;
    }

    private void filterAutoComplete() {
        String prefix = getText(completionReplacementStart, completionPrefixEnd);
        List<CompletionItem> filtered = completionCandidates.stream()
            .filter(item -> item.insertText().startsWith(prefix))
            .toList();

        RRListView<CompletionItem> listView = activeCompletionList.get();
        if (filtered.isEmpty()) {
            hideAutoComplete(false);
        } else if (listView == null) {
            showAutoComplete(filtered);
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
     * When a prefix has no matches, keep the candidates so backspacing can show them again.
     *
     * @param clearState whether to cancel the request and clear its candidates and trigger
     */
    private void hideAutoComplete(boolean clearState) {
        Popup popup = activeCompletionPopup.getAndSet(null);
        if (popup != null) {
            popup.hide();
        }

        activeCompletionList.set(null);

        if (clearState) {
            invalidateCompletionRequest();
            completionCandidates.clear();
            completionReplacementStart = -1;
            completionTriggerOffset = -1;
            completionPrefixEnd = -1;
        }
    }

    private void showAutocomplete(PlainTextChange change) {
        if (codeEditorClosed || providerLifetime.isCancellationRequested())
            return;
        String inserted = change.getInserted();
        try {
            int end = change.getPosition() + inserted.length();
            if (!inserted.isEmpty() && completionProvider.shouldTrigger(inserted)) {
                hideAutoComplete();
                triggerCompletion(end - 1);
            } else if (completionTriggerOffset >= 0 && change.getPosition() > completionTriggerOffset
                && change.getPosition() >= completionReplacementStart
                && change.getRemovalEnd() == completionPrefixEnd
                && inserted.codePoints().allMatch(Character::isJavaIdentifierPart)) {
                completionPrefixEnd = end;
                if (completionCandidates.isEmpty()) {
                    triggerCompletion(completionTriggerOffset, end);
                } else {
                    filterAutoComplete();
                }
            } else {
                hideAutoComplete();
            }
        } catch (CancellationException _) {
            // The session can close before its editor notification reaches the UI thread.
            hideAutoComplete();
        } catch (RuntimeException exception) {
            hideAutoComplete();
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

        caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            if (!signatureEditPending) {
                requestSignatureHelp(false);
            }
        });

        subscriptions.add(plainTextChanges()
            .successionEnds(Duration.ofMillis(120))
            .subscribe(change -> {
                if (!signatureEditPending)
                    return;
                signatureEditPending = false;
                requestSignatureHelp(true);
            }));

        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                hideSignatureHelp();
            }
        });

        focusedProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue) {
                hideSignatureHelp();
            }
        });
    }

    private void requestSignatureHelp(boolean textChanged) {
        if (!manualSignatureHelp && !Settings.AUTOMATIC_PARAMETER_HINTS.getOrDefaultValue())
            return;
        if (!supportsSignatureHelp() || !shouldRequestSignatureHelp(getText(), getCaretPosition(), textChanged)) {
            hideSignatureHelp();
            return;
        }
        requestSignatureHelpAtCaret(manualSignatureHelp ? NAVIGATION_BUDGET : INTERACTIVE_BUDGET, textChanged);
    }

    /** Shows parameter information at the current caret, including after automatic help was dismissed. */
    public void showParameterInfo() {
        if (!supportsSignatureHelp())
            return;
        hideAutoComplete();
        manualSignatureHelp = true;
        signatureEditPending = false;
        requestSignatureHelpAtCaret(NAVIGATION_BUDGET, false);
    }

    private void requestSignatureHelpAtCaret(Duration budget, boolean retainOnEmpty) {
        invalidateSignatureRequest();
        int caret = getCaretPosition();
        int generation = signatureGeneration.incrementAndGet();
        WorkspaceSnapshot workspace = captureFeatureWorkspace();
        if (workspace == null
            || !(workspace.document(documentId).orElse(null) instanceof TextDocumentSnapshot snapshot))
            return;

        activeSignatureCancellation = new CancellationTokenSource(providerLifetime);
        var context = new FeatureRequestContext(workspace, snapshot, activeSignatureCancellation.token(),
            Deadline.after(budget));
        activeSignatureContext = context;
        boolean includeOverloads = manualSignatureHelp;

        featureRequests.submit("signature", RequestPriority.SIGNATURE_HELP, context,
            activeSignatureCancellation, () -> ProviderExecutor.shared().execute(
                context.documentSnapshot().languageId() + ":signature", context, budget,
                () -> signatureHelpProvider.compute(context, caret, includeOverloads)))
            .thenAccept(result -> Platform.runLater(() -> {
                if (signatureGeneration.get() != generation
                    || activeSignatureContext != context
                    || !result.belongsTo(context)
                    || context.cancellationToken().isCancellationRequested()
                    || context.deadline().isExpired()
                    || getCaretPosition() != caret
                    || !isCurrent(context.workspaceSnapshot()))
                    return;

                applySignatureHelp(generation, result.value(), retainOnEmpty);
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

    private void applySignatureHelp(int generation, List<SignatureHelp> signatures, boolean retainOnEmpty) {
        if (signatureGeneration.get() != generation)
            return;

        if (signatures.isEmpty()) {
            if (!retainOnEmpty || !signaturePopup.isShowing()) {
                hideSignatureHelp();
            } else {
                positionSignaturePopup();
            }
            return;
        }

        if (activeSignatureHelp.get().equals(signatures)) {
            positionSignaturePopup();
            return;
        }

        activeSignatureHelp.set(signatures);
        showSignatureHelp(signatures);
    }

    private void showSignatureHelp(List<SignatureHelp> signatures) {
        signatureRows.getChildren().setAll(signatures.stream().map(this::signatureRow).toList());
        signaturePopupContainer.getChildren().clear();
        if (signatures.size() > 8) {
            signatureScroll.setContent(signatureRows);
            signatureScroll.setVvalue(signatureScroll.getVmin());
            signaturePopupContainer.getChildren().add(signatureScroll);
        } else {
            signatureScroll.setContent(null);
            signaturePopupContainer.getChildren().add(signatureRows);
        }
        positionSignaturePopup();
    }

    private TextFlow signatureRow(SignatureHelp help) {
        var row = new TextFlow();
        row.getStyleClass().add("signature-help-text");
        List<SignatureHelp.ParameterInfo> parameters = help.parameters();
        if (parameters.isEmpty()) {
            row.getChildren().add(new Text(L18n.localize("railroad.ide.signature.no_parameters")));
        }
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

            row.getChildren().add(paramText);

            if (i < parameterCount - 1) {
                row.getChildren().add(new Text(", "));
            }
        }

        return row;
    }

    private void positionSignaturePopup() {
        Optional<Bounds> caretBounds = getCaretBounds();
        double x;
        double top;
        double bottom;
        if (caretBounds.isPresent()) {
            Bounds bounds = caretBounds.get();
            x = bounds.getMinX();
            top = bounds.getMinY();
            bottom = bounds.getMaxY();
        } else {
            Point2D editorOrigin = localToScreen(0, 0);
            if (editorOrigin == null)
                return;
            x = editorOrigin.getX();
            top = editorOrigin.getY();
            bottom = top;
        }

        var screen = Screen.getScreensForRectangle(x, top, 1, 1).stream().findFirst().orElse(Screen.getPrimary());
        var available = screen.getVisualBounds();
        double maxContentWidth = Math.max(1, available.getWidth() - 24);
        signatureRows.setMaxWidth(maxContentWidth);
        signatureScroll.setMaxWidth(maxContentWidth);

        double aboveY = top - 6;
        double belowY = bottom + 6;
        boolean above = Settings.PARAMETER_POPUP_POSITION.getOrDefaultValue() == ParameterPopupPosition.ABOVE;
        setSignaturePopupAnchor(above);
        double y = above ? aboveY : belowY;
        if (!signaturePopup.isShowing()) {
            signaturePopup.show(this, x, y);
        } else {
            signaturePopup.setAnchorX(x);
            signaturePopup.setAnchorY(y);
        }

        double roomAbove = aboveY - available.getMinY();
        double roomBelow = available.getMaxY() - belowY;
        double preferredRoom = above ? roomAbove : roomBelow;
        double otherRoom = above ? roomBelow : roomAbove;
        if (signaturePopup.getHeight() > preferredRoom && otherRoom > preferredRoom) {
            setSignaturePopupAnchor(!above);
            signaturePopup.setAnchorY(above ? belowY : aboveY);
        }
    }

    private void setSignaturePopupAnchor(boolean above) {
        signaturePopup.setAnchorLocation(above
            ? PopupWindow.AnchorLocation.WINDOW_BOTTOM_LEFT
            : PopupWindow.AnchorLocation.WINDOW_TOP_LEFT);
    }

    /**
     * Cancels pending signature work and releases the displayed help on the JavaFX thread.
     */
    private void hideSignatureHelp() {
        if (signaturePopup.isShowing()) {
            signaturePopup.hide();
        } else {
            resetSignatureHelp();
        }
    }

    private void resetSignatureHelp() {
        invalidateSignatureRequest();
        manualSignatureHelp = false;
        signatureEditPending = false;
        activeSignatureHelp.set(List.of());
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

    /** @return whether the editor can request parameter information from its language provider */
    public boolean supportsSignatureHelp() {
        return signatureHelpProvider != null && !codeEditorClosed && !providerLifetime.isCancellationRequested();
    }

    protected void onEditorStylesApplied() {
    }

    protected final void reapplyEditorStyles() {
        lastAppliedStyles = null;
        applyEditorStyles();
        restoreBracketHighlight();
    }

}
