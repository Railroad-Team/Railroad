package dev.railroadide.railroad.ide.ui.refactor;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.edit.WorkspaceEditService;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEditPreview;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEditProblem;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEditResult;
import dev.railroadide.railroad.ide.sst.provider.api.ProviderExecutor;
import dev.railroadide.railroad.ide.sst.provider.api.RenameConflict;
import dev.railroadide.railroad.ide.sst.provider.api.RenamePreparation;
import dev.railroadide.railroad.ide.sst.provider.api.RenameProposal;
import dev.railroadide.railroad.ide.sst.provider.api.RenameProvider;
import dev.railroadide.railroad.ide.sst.request.api.*;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRTextField;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.window.WindowBuilder;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Asks for a symbol's new name, previews every file and change the rename makes together with its conflicts, and
 * applies the previewed edit through the project's workspace-edit service as one undoable step.
 * <p>
 * The first confirmation computes the preview; confirming again applies exactly the previewed edit. Changing the
 * name discards the preview. The rename is computed against a fresh capture of the editor's document store,
 * including unsaved documents, and the workspace-edit service rejects it if any affected document changes before
 * it is applied. All methods must be called on the JavaFX application thread.
 */
public final class RenameDialog implements AutoCloseable {
    private static final Duration RENAME_BUDGET = Duration.ofSeconds(60);

    private final Request request;
    private final SstScheduler.Scope requests = SstScheduler.shared().newScope();
    private final RRTextField nameField = new RRTextField();
    private final LocalizedLabel status = new LocalizedLabel("railroad.rename.status.enter_name");
    private final RRVBox conflictList = new RRVBox(4);
    private final WorkspaceEditPreviewView changes = new WorkspaceEditPreviewView();
    private final RRButton confirmButton = new RRButton("railroad.rename.preview");
    private final RRVBox content;
    private @Nullable Stage stage;
    private @Nullable CancellationTokenSource activeCancellation;
    private @Nullable FeatureRequestContext activeContext;
    private @Nullable RenameProposal proposal;
    private State state = State.EDITING;
    private boolean closed;

    /**
     * Captured inputs of one rename.
     *
     * @param provider rename provider of the document's language session
     * @param providerLifetime lifetime of the provider's session; cancelling it cancels pending computations
     * @param store document store the rename is computed against
     * @param prepared document revision in which the symbol was prepared
     * @param offset UTF-16 offset of the symbol in the prepared revision
     * @param symbol prepared symbol
     * @param edits project workspace-edit service, resolved when the rename is applied
     */
    public record Request(
        RenameProvider provider,
        CancellationToken providerLifetime,
        DocumentStore store,
        TextDocumentSnapshot prepared,
        int offset,
        RenamePreparation.Renameable symbol,
        Supplier<Optional<WorkspaceEditService>> edits
    ) {
        /**
         * Validates that all inputs are present.
         *
         * @param provider rename provider
         * @param providerLifetime provider session lifetime
         * @param store document store
         * @param prepared prepared document revision
         * @param offset UTF-16 offset of the symbol
         * @param symbol prepared symbol
         * @param edits project workspace-edit service supplier
         */
        public Request {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(providerLifetime, "providerLifetime");
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(prepared, "prepared");
            Objects.requireNonNull(symbol, "symbol");
            Objects.requireNonNull(edits, "edits");
        }
    }

    /** Progress of the dialog. */
    public enum State {
        /** The name is being edited and no preview is shown. */
        EDITING,
        /** The rename is being computed. */
        COMPUTING,
        /** A preview that can be applied is shown. */
        PREVIEWED,
        /** The rename cannot be applied; conflicts or problems are shown. */
        BLOCKED,
        /** The rename was applied. */
        APPLIED
    }

    /**
     * Builds the dialog without showing it.
     *
     * @param request captured rename inputs
     */
    public RenameDialog(Request request) {
        this.request = Objects.requireNonNull(request, "request");
        var title = new LocalizedLabel("railroad.rename.heading", request.symbol().kind(), request.symbol().name());
        title.getStyleClass().add("rename-heading");

        nameField.setText(request.symbol().name());
        nameField.textProperty().addListener((_, _, _) -> discardPreview());
        nameField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                confirm();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                close();
                event.consume();
            }
        });

        status.setWrapText(true);
        conflictList.getStyleClass().add("rename-conflicts");
        changes.getStyleClass().add("rename-preview");
        changes.setPrefHeight(320);
        RRVBox.setVgrow(changes, Priority.ALWAYS);

        confirmButton.setVariant(ButtonVariant.PRIMARY);
        confirmButton.setOnAction(_ -> confirm());
        var cancelButton = new RRButton("railroad.rename.cancel");
        cancelButton.setVariant(ButtonVariant.SECONDARY);
        cancelButton.setOnAction(_ -> close());
        var buttons = new RRHBox(10, cancelButton, confirmButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        content = new RRVBox(12, title, new LocalizedLabel("railroad.rename.new_name"), nameField, status,
            conflictList, changes, buttons);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("rename-dialog");
    }

    /**
     * Shows the dialog with the current name selected.
     *
     * @param owner window that owns the dialog, or {@code null}
     */
    public void show(@Nullable Window owner) {
        if (closed || stage != null)
            return;
        stage = WindowBuilder.create()
            .title(L18n.localize("railroad.rename.title", request.symbol().kind()), false)
            .owner(owner)
            .modality(Modality.WINDOW_MODAL)
            .scene(new Scene(content))
            .size(720, 560)
            .minSize(520, 360)
            .build();
        stage.setOnHidden(_ -> close());
        nameField.requestFocus();
        nameField.selectAll();
    }

    /**
     * Replaces the requested name, discarding any preview.
     *
     * @param name requested name
     */
    public void setNewName(String name) {
        nameField.setText(Objects.requireNonNull(name, "name"));
    }

    /**
     * Previews the rename when no preview is shown, or applies the shown preview. This is what pressing Enter or the
     * primary button does.
     */
    public void confirm() {
        if (state == State.PREVIEWED) {
            apply();
        } else if (state == State.EDITING || state == State.BLOCKED) {
            preview();
        }
    }

    /**
     * Computes the rename for the current name and shows its preview and conflicts.
     *
     * @return future completing on the JavaFX thread once the preview is shown, or exceptionally when the rename
     *         could not be computed
     */
    public CompletableFuture<State> preview() {
        cancelComputation();
        proposal = null;
        clearPreview();
        if (closed)
            return CompletableFuture.completedFuture(state);

        WorkspaceSnapshot workspace = request.store().capture();
        if (!(workspace.document(request.prepared().id()).orElse(null) instanceof TextDocumentSnapshot source)
            || !source.text().equals(request.prepared().text())) {
            block("railroad.rename.status.document_changed");
            return CompletableFuture.completedFuture(state);
        }

        String name = nameField.getText();
        var cancellation = new CancellationTokenSource(request.providerLifetime());
        var context = new FeatureRequestContext(workspace, source, cancellation.token(),
            Deadline.after(RENAME_BUDGET));
        activeCancellation = cancellation;
        activeContext = context;
        state = State.COMPUTING;
        status.setKey("railroad.rename.status.computing");
        confirmButton.setDisable(true);

        var shown = new CompletableFuture<State>();
        CompletableFuture<FeatureResult<RenameProposal>> future;
        try {
            future = requests.submit("rename", RequestPriority.NAVIGATION, context, cancellation,
                () -> ProviderExecutor.shared().execute(source.languageId() + ":rename", context, RENAME_BUDGET,
                    () -> request.provider().rename(context, request.offset(), name)));
        } catch (RuntimeException exception) {
            future = CompletableFuture.failedFuture(exception);
        }
        future.whenComplete((result, failure) -> Platform.runLater(() -> {
            if (activeContext != context) {
                shown.complete(state);
                return;
            }
            activeContext = null;
            activeCancellation = null;
            if (failure != null || result == null || !result.belongsTo(context)) {
                Throwable cause = unwrap(failure);
                if (cause != null && !(cause instanceof CancellationException)) {
                    Railroad.LOGGER.error("Failed to compute rename", cause);
                }
                block(cause instanceof DeadlineExceededException
                    ? "railroad.rename.status.timed_out"
                    : "railroad.rename.status.failed");
                shown.complete(state);
                return;
            }
            show(result.value());
            shown.complete(state);
        }));
        return shown;
    }

    /**
     * Applies the shown preview through the project's workspace-edit service.
     *
     * @return outcome, or an empty optional when no applicable preview is shown or the project has no edit service
     */
    public Optional<WorkspaceEditResult> apply() {
        RenameProposal current = proposal;
        if (state != State.PREVIEWED || current == null || current.edit() == null)
            return Optional.empty();
        Optional<WorkspaceEditService> service = request.edits().get();
        if (service.isEmpty()) {
            block("railroad.rename.status.no_project");
            return Optional.empty();
        }

        WorkspaceEditResult result = service.get().apply(current.edit());
        if (result.applied()) {
            state = State.APPLIED;
            close();
        } else {
            showProblems(result.problems());
            block("railroad.rename.status.apply_failed");
        }
        return Optional.of(result);
    }

    /**
     * Returns the dialog's progress.
     *
     * @return current state
     */
    public State state() {
        return state;
    }

    /**
     * Returns the proposal whose preview is shown.
     *
     * @return shown proposal, or an empty optional
     */
    public Optional<RenameProposal> proposal() {
        return Optional.ofNullable(proposal);
    }

    /**
     * Returns the lines of the shown preview: one per changed file or file rename, followed by its changes.
     *
     * @return preview lines in display order
     */
    public List<String> previewLines() {
        return changes.lines();
    }

    /**
     * Returns the conflict and problem messages shown.
     *
     * @return messages in display order
     */
    public List<String> messages() {
        return conflictList.getChildren().stream().map(node -> ((Label) node).getText()).toList();
    }

    /**
     * Returns the status message shown.
     *
     * @return status text
     */
    public String statusText() {
        return status.getText();
    }

    /**
     * Reports whether the dialog was closed.
     *
     * @return whether the dialog is closed
     */
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed)
            return;
        closed = true;
        cancelComputation();
        requests.close();
        if (stage != null) {
            stage.setOnHidden(null);
            stage.hide();
        }
    }

    private void show(RenameProposal result) {
        proposal = result;
        result.conflicts().forEach(conflict -> addMessage(describe(conflict),
            conflict.severity() == RenameConflict.Severity.ERROR ? "rename-error" : "rename-warning"));
        if (!result.applicable()) {
            block("railroad.rename.status.conflicts");
            return;
        }

        Optional<WorkspaceEditService> service = request.edits().get();
        if (service.isEmpty()) {
            block("railroad.rename.status.no_project");
            return;
        }
        WorkspaceEditPreview preview = service.get().preview(result.edit());
        if (!preview.applicable()) {
            showProblems(preview.problems());
            block("railroad.rename.status.apply_failed");
            return;
        }

        int count = changes.show(preview);

        state = State.PREVIEWED;
        status.setKey(result.conflicts().isEmpty()
            ? "railroad.rename.status.ready"
            : "railroad.rename.status.ready_with_warnings", count, preview.documents().size());
        confirmButton
            .setLocalizedText(result.conflicts().isEmpty() ? "railroad.rename.apply" : "railroad.rename.apply_anyway");
        confirmButton.setDisable(false);
    }

    private static String describe(RenameConflict conflict) {
        return conflict.message();
    }

    private void showProblems(List<WorkspaceEditProblem> problems) {
        problems.forEach(problem -> addMessage(problem.message()
            + (problem.uri() == null ? "" : " (" + fileName(problem.uri()) + ")"), "rename-error"));
    }

    private void addMessage(String message, String style) {
        var label = new Label(message);
        label.setWrapText(true);
        label.getStyleClass().add(style);
        conflictList.getChildren().add(label);
    }

    private void block(String key) {
        state = State.BLOCKED;
        status.setKey(key);
        confirmButton.setLocalizedText("railroad.rename.preview");
        confirmButton.setDisable(false);
    }

    private void discardPreview() {
        cancelComputation();
        proposal = null;
        clearPreview();
        if (state != State.APPLIED) {
            state = State.EDITING;
            status.setKey("railroad.rename.status.enter_name");
            confirmButton.setLocalizedText("railroad.rename.preview");
            confirmButton.setDisable(false);
        }
    }

    private void clearPreview() {
        conflictList.getChildren().clear();
        changes.clear();
    }

    private void cancelComputation() {
        requests.cancel("rename");
        if (activeCancellation != null) {
            activeCancellation.cancel();
            activeCancellation = null;
        }
        activeContext = null;
    }

    private static String fileName(@Nullable DocumentUri uri) {
        return uri == null ? "" : uri.fileName().orElse(uri.toString());
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
