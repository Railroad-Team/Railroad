package dev.railroadide.railroad.ide.ui.codeaction;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic.TextEditorDiagnostic;
import dev.railroadide.railroad.ide.edit.WorkspaceEditService;
import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.DocumentStore;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceSnapshot;
import dev.railroadide.railroad.ide.sst.edit.api.ChangeAnnotation;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceChange;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEdit;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEditPreview;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEditProblem;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEditResult;
import dev.railroadide.railroad.ide.sst.provider.api.CodeAction;
import dev.railroadide.railroad.ide.sst.provider.api.CodeActionProvider;
import dev.railroadide.railroad.ide.sst.provider.api.CodeActionRequest;
import dev.railroadide.railroad.ide.sst.provider.api.ProviderExecutor;
import dev.railroadide.railroad.ide.sst.request.api.*;
import javafx.application.Platform;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Discovers, lazily resolves and applies the code actions of one editor's document.
 * <p>
 * Discovery and resolution run on SST workers against an immutable workspace capture; each new request supersedes
 * and cancels the previous one of its kind. Results are validated before use: actions whose diagnostics or edits
 * belong to another document revision are dropped, a resolution is refused once the document has changed since its
 * discovery, and every edit is previewed and applied only through the project's {@link WorkspaceEditService},
 * which owns conflict detection, undo and redo. Closing the controller, or cancelling its provider lifetime,
 * cancels all outstanding work.
 * <p>
 * All methods must be called on the JavaFX application thread; returned futures complete on it.
 */
public final class CodeActionController implements AutoCloseable {
    private static final Duration DISCOVERY_BUDGET = Duration.ofSeconds(5);
    private static final Duration RESOLUTION_BUDGET = Duration.ofSeconds(15);
    private static final String DISCOVER = "discover";
    private static final String RESOLVE = "resolve";

    private final CodeActionProvider provider;
    private final CancellationToken lifetime;
    private final Supplier<@Nullable DocumentStore> store;
    private final Supplier<Optional<WorkspaceEditService>> edits;
    private final SstScheduler.Scope requests = SstScheduler.shared().newScope();
    private @Nullable CancellationTokenSource discovery;
    private @Nullable CancellationTokenSource resolution;
    private boolean closed;

    /**
     * Actions discovered for one captured document revision.
     *
     * @param workspace captured workspace the actions were computed for
     * @param document captured document revision
     * @param request requested range and diagnostics
     * @param actions validated actions in provider order with preferred actions first
     */
    public record Discovery(
        WorkspaceSnapshot workspace,
        TextDocumentSnapshot document,
        CodeActionRequest request,
        List<CodeAction> actions
    ) {
        /**
         * Copies the actions.
         *
         * @param workspace captured workspace
         * @param document captured document revision
         * @param request requested range and diagnostics
         * @param actions validated actions
         */
        public Discovery {
            Objects.requireNonNull(workspace, "workspace");
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(request, "request");
            actions = List.copyOf(actions);
        }

        /**
         * Reports whether any action can be applied.
         *
         * @return whether at least one action is available
         */
        public boolean hasAvailableActions() {
            return actions.stream().anyMatch(CodeAction::available);
        }
    }

    /** Outcome of resolving an action. */
    public enum Status {
        /** The action's edit is computed and its preview can be applied. */
        READY,
        /** The provider reports that the action cannot be applied. */
        UNAVAILABLE,
        /** The document changed since the action was discovered. */
        STALE,
        /** The provider cannot compute an edit for the action. */
        UNSUPPORTED,
        /** Computing or previewing the edit failed. */
        FAILED
    }

    /**
     * A resolved action.
     *
     * @param status outcome
     * @param discovery discovery the action belongs to
     * @param action the resolved action, or the selected one when resolution failed
     * @param preview preview of the action's edit, or {@code null} when none was computed
     * @param message user-facing explanation when the action cannot be applied, otherwise empty
     */
    public record Resolution(
        Status status,
        Discovery discovery,
        CodeAction action,
        @Nullable WorkspaceEditPreview preview,
        String message
    ) {
        /**
         * Validates a resolution.
         *
         * @param status outcome
         * @param discovery owning discovery
         * @param action resolved or selected action
         * @param preview preview, or {@code null}
         * @param message explanation, or empty
         */
        public Resolution {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(discovery, "discovery");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(message, "message");
            if (status == Status.READY && (preview == null || action.edit() == null))
                throw new IllegalArgumentException("A ready resolution needs an edit and its preview");
        }

        /**
         * Reports whether the resolved edit changes anything beyond the discovered document's text, so that it
         * should be confirmed before it is applied.
         *
         * @return whether other documents or files are affected
         */
        public boolean affectsOtherDocuments() {
            return preview != null && (!preview.resources().isEmpty() || preview.documents().stream()
                .anyMatch(document -> !document.document().equals(discovery.document().id())));
        }

        /**
         * Returns the labels of the annotations that ask for the resolved edit's changes to be confirmed.
         *
         * @return distinct labels in edit order; empty when no applied change needs confirmation
         */
        public List<String> confirmationLabels() {
            WorkspaceEdit edit = action.edit();
            if (edit == null)
                return List.of();
            Set<String> labels = new LinkedHashSet<>();
            for (WorkspaceChange change : edit.changes()) {
                ChangeAnnotation annotation = change.annotationId() == null
                    ? null
                    : edit.annotations().get(change.annotationId());
                if (annotation != null && annotation.needsConfirmation()) {
                    labels.add(annotation.label());
                }
            }
            return List.copyOf(labels);
        }

        /**
         * Reports whether the user must confirm the resolved edit before it is applied: because it changes more
         * than the discovered document, or because one of its changes is annotated as needing confirmation.
         *
         * @return whether applying needs a confirmation
         */
        public boolean needsConfirmation() {
            return affectsOtherDocuments() || !confirmationLabels().isEmpty();
        }
    }

    /**
     * Creates a controller for one editor.
     *
     * @param provider code action provider of the document's language session
     * @param lifetime language session lifetime; its cancellation cancels all requests
     * @param store document store the editor is attached to, or {@code null} while detached
     * @param edits project workspace-edit service
     */
    public CodeActionController(
        CodeActionProvider provider,
        CancellationToken lifetime,
        Supplier<@Nullable DocumentStore> store,
        Supplier<Optional<WorkspaceEditService>> edits
    ) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
        this.store = Objects.requireNonNull(store, "store");
        this.edits = Objects.requireNonNull(edits, "edits");
    }

    /**
     * Discovers the actions for a range of a captured document, superseding any discovery in progress.
     * Diagnostics that do not belong to the captured revision are not passed to the provider.
     *
     * @param workspace captured workspace containing the document
     * @param document captured document revision
     * @param request requested range and diagnostics
     * @param priority scheduling priority, higher for explicit invocations
     * @return validated actions, or a future failing with {@link CancellationException} when superseded
     */
    public CompletableFuture<Discovery> discover(
        WorkspaceSnapshot workspace,
        TextDocumentSnapshot document,
        CodeActionRequest request,
        RequestPriority priority
    ) {
        cancelDiscovery();
        if (closed || lifetime.isCancellationRequested())
            return CompletableFuture.failedFuture(new CancellationException("Code actions are closed"));

        var filtered = new CodeActionRequest(request.range(),
            request.diagnostics().stream().filter(diagnostic -> belongsTo(diagnostic, document)).toList());
        var cancellation = new CancellationTokenSource(lifetime);
        var context = new FeatureRequestContext(workspace, document, cancellation.token(),
            Deadline.after(DISCOVERY_BUDGET));
        discovery = cancellation;
        return onFx(requests.submit(DISCOVER, priority, context, cancellation,
            () -> ProviderExecutor.shared().execute(document.languageId() + ":code-actions", context,
                DISCOVERY_BUDGET, () -> provider.actions(context, filtered))))
            .thenApply(result -> {
                if (discovery != cancellation || cancellation.token().isCancellationRequested()
                    || !result.belongsTo(context))
                    throw new CancellationException("Code action discovery was superseded");
                discovery = null;
                return new Discovery(workspace, document, filtered, validated(result.value(), workspace, document));
            });
    }

    /**
     * Resolves an action of a discovery and previews its edit, superseding any resolution in progress. The provider
     * is asked to compute the edit only when the action does not carry one.
     *
     * @param discovered discovery that returned the action
     * @param action action to resolve
     * @return resolution, or a future failing with {@link CancellationException} when superseded
     */
    public CompletableFuture<Resolution> resolve(Discovery discovered, CodeAction action) {
        Objects.requireNonNull(discovered, "discovered");
        Objects.requireNonNull(action, "action");
        cancelResolution();
        if (closed || lifetime.isCancellationRequested())
            return CompletableFuture.failedFuture(new CancellationException("Code actions are closed"));
        if (!action.available())
            return CompletableFuture.completedFuture(new Resolution(Status.UNAVAILABLE, discovered, action, null,
                Objects.requireNonNull(action.unavailableReason())));
        if (!isCurrent(discovered.document()))
            return CompletableFuture.completedFuture(stale(discovered, action));
        if (action.isResolved())
            return CompletableFuture.completedFuture(preview(discovered, action));

        var cancellation = new CancellationTokenSource(lifetime);
        var context = new FeatureRequestContext(discovered.workspace(), discovered.document(), cancellation.token(),
            Deadline.after(RESOLUTION_BUDGET));
        resolution = cancellation;
        CompletableFuture<FeatureResult<CodeAction>> computed = requests.submit(RESOLVE, RequestPriority.NAVIGATION,
            context, cancellation, () -> ProviderExecutor.shared().execute(
                discovered.document().languageId() + ":code-action-resolve", context, RESOLUTION_BUDGET,
                () -> provider.resolve(context, action)));
        var resolved = new CompletableFuture<Resolution>();
        computed.whenComplete((result, failure) -> Platform.runLater(() -> {
            if (resolution != cancellation || cancellation.token().isCancellationRequested()) {
                resolved.completeExceptionally(new CancellationException("Code action resolution was superseded"));
                return;
            }
            resolution = null;
            Throwable cause = unwrap(failure);
            if (cause instanceof CancellationException) {
                resolved.completeExceptionally(cause);
            } else if (cause != null) {
                resolved.complete(failed(discovered, action, cause));
            } else {
                try {
                    resolved.complete(accept(discovered, action, result, context));
                } catch (RuntimeException exception) {
                    resolved.complete(failed(discovered, action, exception));
                }
            }
        }));
        return resolved;
    }

    /**
     * Applies a ready resolution through the project's workspace-edit service as one undoable step. Nothing is
     * changed when the provider's language session ended, the document changed since discovery or the service
     * rejects the edit. Confirmation is the caller's duty: see {@link Resolution#needsConfirmation()}.
     *
     * @param resolved resolution whose status is {@link Status#READY}
     * @return outcome of the application
     * @throws IllegalArgumentException if the resolution is not ready
     */
    public WorkspaceEditResult apply(Resolution resolved) {
        Objects.requireNonNull(resolved, "resolved");
        if (resolved.status() != Status.READY)
            throw new IllegalArgumentException("Only ready resolutions can be applied");
        TextDocumentSnapshot document = resolved.discovery().document();
        if (lifetime.isCancellationRequested())
            return rejected(WorkspaceEditProblem.Kind.INVALID, document,
                "The language support that offered the action is no longer loaded");
        if (closed || !isCurrent(document))
            return rejected(WorkspaceEditProblem.Kind.STALE, document,
                "The document changed after the action was computed");
        Optional<WorkspaceEditService> service = edits.get();
        if (service.isEmpty())
            return rejected(WorkspaceEditProblem.Kind.INVALID, document, "No project is open to apply the action to");
        return service.get().apply(Objects.requireNonNull(resolved.action().edit()));
    }

    /**
     * Cancels discovery and resolution in progress.
     */
    public void cancel() {
        cancelDiscovery();
        cancelResolution();
    }

    /**
     * Reports whether the controller was closed.
     *
     * @return whether {@link #close()} was called
     */
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed)
            return;
        closed = true;
        cancel();
        requests.close();
    }

    private Resolution accept(
        Discovery discovered,
        CodeAction selected,
        @Nullable FeatureResult<CodeAction> result,
        FeatureRequestContext context
    ) {
        if (result == null || !result.belongsTo(context) || result.value() == null)
            return new Resolution(Status.FAILED, discovered, selected, null, "The action could not be computed");
        CodeAction action = result.value();
        if (!action.available())
            return new Resolution(Status.UNAVAILABLE, discovered, action, null,
                Objects.requireNonNull(action.unavailableReason()));
        if (action.edit() == null)
            return new Resolution(Status.UNSUPPORTED, discovered, action, null, "This action cannot be applied");
        if (!boundTo(action.edit(), discovered.workspace())) {
            Railroad.LOGGER.warn("Code action '{}' resolved to changes of another document revision", action.title());
            return new Resolution(Status.FAILED, discovered, action, null, "The action could not be computed");
        }
        if (!isCurrent(discovered.document()))
            return stale(discovered, action);
        return preview(discovered, action);
    }

    private Resolution preview(Discovery discovered, CodeAction action) {
        Optional<WorkspaceEditService> service = edits.get();
        if (service.isEmpty())
            return new Resolution(Status.FAILED, discovered, action, null, "No project is open to apply the action to");
        WorkspaceEditPreview preview;
        try {
            preview = service.get().preview(Objects.requireNonNull(action.edit()));
        } catch (IllegalStateException exception) {
            return failed(discovered, action, exception);
        }
        if (!preview.applicable())
            return new Resolution(Status.FAILED, discovered, action, preview, preview.problems().stream()
                .map(WorkspaceEditProblem::message).collect(Collectors.joining("\n")));
        return new Resolution(Status.READY, discovered, action, preview, "");
    }

    private static Resolution stale(Discovery discovered, CodeAction action) {
        return new Resolution(Status.STALE, discovered, action, null,
            "The document changed after the actions were computed");
    }

    private static Resolution failed(Discovery discovered, CodeAction action, Throwable cause) {
        if (cause instanceof UnsupportedOperationException)
            return new Resolution(Status.UNSUPPORTED, discovered, action, null, "This action cannot be applied");
        if (cause instanceof DeadlineExceededException)
            return new Resolution(Status.FAILED, discovered, action, null, "Computing the action timed out");
        Railroad.LOGGER.error("Failed to resolve code action '{}'", action.title(), cause);
        return new Resolution(Status.FAILED, discovered, action, null, "The action could not be computed");
    }

    private static List<CodeAction> validated(
        @Nullable List<CodeAction> actions,
        WorkspaceSnapshot workspace,
        TextDocumentSnapshot document
    ) {
        if (actions == null)
            return List.of();
        List<CodeAction> valid = new ArrayList<>();
        for (CodeAction action : actions) {
            if (action == null)
                continue;
            if (action.diagnostics().stream().allMatch(diagnostic -> belongsTo(diagnostic, document))
                && (action.edit() == null || boundTo(action.edit(), workspace))) {
                valid.add(action);
            } else {
                Railroad.LOGGER.warn("Discarding code action '{}' computed for another document revision",
                    action.title());
            }
        }
        valid.sort(Comparator.comparing(action -> !action.preferred()));
        return valid;
    }

    private static boolean belongsTo(TextEditorDiagnostic diagnostic, TextDocumentSnapshot document) {
        try {
            diagnostic.location().validateAgainst(document);
            return true;
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            return false;
        }
    }

    private static boolean boundTo(WorkspaceEdit edit, WorkspaceSnapshot workspace) {
        for (WorkspaceChange change : edit.changes()) {
            DocumentSnapshot expected = switch (change) {
                case WorkspaceChange.TextEdit text -> text.expected();
                case WorkspaceChange.DeleteFile delete -> delete.expected();
                case WorkspaceChange.RenameFile rename -> rename.expected();
                case WorkspaceChange.CreateFile _ -> null;
            };
            if (expected != null && !workspace.document(expected.id()).map(expected::equals).orElse(false))
                return false;
        }
        return true;
    }

    private boolean isCurrent(TextDocumentSnapshot document) {
        DocumentStore current = store.get();
        return current != null && current.current(document.id()).map(document::equals).orElse(false);
    }

    private static WorkspaceEditResult rejected(
        WorkspaceEditProblem.Kind kind,
        TextDocumentSnapshot document,
        String message
    ) {
        return new WorkspaceEditResult(WorkspaceEditResult.Status.REJECTED,
            List.of(new WorkspaceEditProblem(kind, -1, document.uri(), message)));
    }

    /**
     * Cancels the discovery in progress.
     */
    public void cancelDiscovery() {
        requests.cancel(DISCOVER);
        if (discovery != null) {
            discovery.cancel();
            discovery = null;
        }
    }

    /**
     * Cancels the resolution in progress.
     */
    public void cancelResolution() {
        requests.cancel(RESOLVE);
        if (resolution != null) {
            resolution.cancel();
            resolution = null;
        }
    }

    private static <T> CompletableFuture<T> onFx(CompletableFuture<T> future) {
        var result = new CompletableFuture<T>();
        future.whenComplete((value, failure) -> Platform.runLater(() -> {
            if (failure != null) {
                result.completeExceptionally(unwrap(failure));
            } else {
                result.complete(value);
            }
        }));
        return result;
    }

    private static @Nullable Throwable unwrap(@Nullable Throwable throwable) {
        Throwable cause = throwable;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
            && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
