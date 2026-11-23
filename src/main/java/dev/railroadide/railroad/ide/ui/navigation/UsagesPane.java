package dev.railroadide.railroad.ide.ui.navigation;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.provider.api.*;
import dev.railroadide.railroad.ide.sst.request.api.*;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.localized.LocalizedTooltip;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.utility.FileUtils;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import org.jspecify.annotations.Nullable;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shows the streamed results of one Find Usages search, grouped by document.
 * <p>
 * All state is owned by the JavaFX thread. A new search, Cancel, or closing the pane cancels the running request;
 * batches from superseded requests are discarded. Results stay bound to the document revisions they were found
 * in: once the workspace changes they are marked outdated, opening an outdated usage is refused instead of
 * selecting shifted text, and Refresh repeats the search for the same symbol identity against a new capture.
 */
public final class UsagesPane extends BorderPane implements AutoCloseable {
    private static final Duration SEARCH_BUDGET = Duration.ofSeconds(60);
    private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");
    private static final PseudoClass FAILED = PseudoClass.getPseudoClass("failed");

    private final SstScheduler.Scope requests = SstScheduler.shared().newScope();
    private final @Nullable Path projectRoot;
    private final Label heading = new Label();
    private final Label status = new Label();
    private final ProgressBar progress = new ProgressBar();
    private final RRButton refresh = toolButton("railroad.ide.usages.refresh", FontAwesomeSolid.SYNC_ALT);
    private final RRButton cancel = toolButton("railroad.ide.usages.cancel", FontAwesomeSolid.STOP);
    private final HBox groupActions = new HBox();
    private final VBox header = new VBox();
    private final FontIcon emptyIcon = new FontIcon();
    private final Label emptyTitle = new Label();
    private final Label emptyDetail = new Label();
    private final VBox empty = new VBox(emptyIcon, emptyTitle, emptyDetail);
    private final TreeItem<Row> root = new TreeItem<>();
    private final TreeView<Row> tree = new TreeView<>(root);
    private final Map<DocumentId, TreeItem<Row>> groups = new LinkedHashMap<>();
    private final Set<Location.TextLocation> shown = new HashSet<>();
    private final AtomicBoolean outdatedCheckQueued = new AtomicBoolean();

    private @Nullable Request request;
    private @Nullable FeatureRequestContext activeContext;
    private @Nullable CancellationTokenSource activeCancellation;
    private DocumentStore.@Nullable ChangeSubscription storeSubscription;
    private @Nullable SymbolId symbol;
    private @Nullable String symbolName;
    private String failureMessage = "";
    private State state = State.IDLE;
    private int searchedDocuments;
    private int totalDocuments;
    private boolean closed;

    /**
     * Captured inputs of one search. The workspace and source must come from the same store capture.
     *
     * @param provider navigation provider of the source document's language session
     * @param providerLifetime lifetime of the provider's session; cancelling it cancels the search
     * @param store document store used to detect outdated results and to refresh
     * @param workspace captured workspace to search
     * @param source captured document the query is resolved in
     * @param query offset or symbol whose usages are searched
     */
    public record Request(
        NavigationProvider provider,
        CancellationToken providerLifetime,
        DocumentStore store,
        WorkspaceSnapshot workspace,
        TextDocumentSnapshot source,
        UsageQuery query
    ) {
        /**
         * Validates that all inputs are present.
         *
         * @param provider navigation provider of the source document's language session
         * @param providerLifetime lifetime of the provider's session
         * @param store document store used to detect outdated results and to refresh
         * @param workspace captured workspace to search
         * @param source captured document the query is resolved in
         * @param query offset or symbol whose usages are searched
         */
        public Request {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(providerLifetime, "providerLifetime");
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(workspace, "workspace");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(query, "query");
        }
    }

    /**
     * Creates an empty results pane.
     */
    public UsagesPane() {
        this(null);
    }

    /**
     * Creates a results pane with paths relative to the project when available.
     *
     * @param project project whose workspace is searched
     */
    public UsagesPane(@Nullable Project project) {
        projectRoot = project == null ? null : project.getPath().toAbsolutePath().normalize();
        getStyleClass().add("usages-pane");
        refresh.setOnAction(_ -> refresh());
        cancel.setOnAction(_ -> cancelSearch());
        var collapse = toolButton("railroad.generic.collapse_all", FontAwesomeSolid.COMPRESS_ALT);
        collapse.setOnAction(_ -> groups.values().forEach(item -> item.setExpanded(false)));
        var expand = toolButton("railroad.generic.expand_all", FontAwesomeSolid.EXPAND_ALT);
        expand.setOnAction(_ -> groups.values().forEach(item -> item.setExpanded(true)));
        groupActions.getChildren().addAll(collapse, expand);
        groupActions.getStyleClass().add("usages-group-actions");
        heading.getStyleClass().add("usages-heading");
        heading.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
        status.getStyleClass().add("usages-summary");
        for (var label : List.of(heading, status)) {
            var tooltip = new Tooltip();
            tooltip.textProperty().bind(label.textProperty());
            label.setTooltip(tooltip);
            label.setMinWidth(0);
        }
        var caption = new VBox(heading, status);
        caption.setMinWidth(0);
        HBox.setHgrow(caption, Priority.ALWAYS);
        var searchIcon = new FontIcon(FontAwesomeSolid.SEARCH);
        searchIcon.getStyleClass().add("usages-search-icon");
        var iconTile = new StackPane(searchIcon);
        iconTile.getStyleClass().add("usages-search-tile");
        var toolbar = new HBox(iconTile, caption, groupActions, cancel, refresh);
        toolbar.getStyleClass().add("usages-toolbar");
        progress.getStyleClass().add("usages-progress");
        progress.setMaxWidth(Double.MAX_VALUE);
        header.getChildren().addAll(toolbar, progress);
        setTop(header);

        tree.getStyleClass().addAll("rr-tree-view", "usages-tree");
        tree.setShowRoot(false);
        tree.setCellFactory(_ -> new RowCell());
        tree.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                openSelected();
            }
        });
        tree.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                openSelected();
                event.consume();
            }
        });
        empty.getStyleClass().add("usages-empty");
        empty.setMaxSize(460, USE_PREF_SIZE);
        emptyTitle.getStyleClass().add("usages-empty-title");
        emptyDetail.getStyleClass().add("usages-empty-detail");
        emptyTitle.setWrapText(true);
        emptyDetail.setWrapText(true);
        setCenter(new StackPane(tree, empty));
        for (var node : List.of(header, status, progress, groupActions, cancel, refresh, tree, empty)) {
            node.managedProperty().bind(node.visibleProperty());
        }
        updateStatus();
    }

    private static RRButton toolButton(String key, FontAwesomeSolid icon) {
        var button = new RRButton("", icon);
        button.setVariant(ButtonVariant.GHOST);
        button.getStyleClass().add("toolbar-button");
        var tooltip = new LocalizedTooltip(key);
        button.setTooltip(tooltip);
        button.accessibleTextProperty().bind(tooltip.textProperty());
        return button;
    }

    private static String usageCount(int count) {
        return L18n.localize(count == 1 ? "railroad.ide.usages.one_usage" : "railroad.ide.usages.usage_count", count);
    }

    private static String fileCount(int count) {
        return L18n.localize(count == 1 ? "railroad.ide.usages.one_file" : "railroad.ide.usages.file_count", count);
    }

    /**
     * Repeats the last search against a new capture of its store. Searches whose symbol was resolved are repeated
     * by symbol identity; otherwise the original offset is reused only while its document is unchanged.
     */
    public void refresh() {
        Request previous = request;
        if (previous == null || closed)
            return;

        WorkspaceSnapshot workspace = previous.store().capture();
        if (!(workspace.document(previous.source().id()).orElse(null) instanceof TextDocumentSnapshot source))
            return;

        UsageQuery query;
        if (symbol != null) {
            query = new UsageQuery.OfSymbol(symbol);
        } else if (source.version().equals(previous.source().version())) {
            query = previous.query();
        } else
            return;

        search(new Request(previous.provider(), previous.providerLifetime(), previous.store(), workspace, source,
            query));
    }

    /**
     * Cancels the running search and keeps the partial results it produced.
     */
    public void cancelSearch() {
        if (state != State.SEARCHING)
            return;

        invalidateRequest();
        state = State.CANCELLED;
        updateStatus();
    }

    /**
     * Returns the presented search state.
     *
     * @return current state
     */
    public State state() {
        return state;
    }

    /**
     * Returns the usages currently listed, in display order.
     *
     * @return listed usage locations
     */
    public List<Location.TextLocation> usages() {
        return root.getChildren().stream()
            .flatMap(group -> group.getChildren().stream())
            .map(TreeItem::getValue)
            .filter(UsageRow.class::isInstance)
            .map(row -> ((UsageRow) row).location())
            .toList();
    }

    /**
     * Returns the user-facing status line.
     *
     * @return status text
     */
    public String statusText() {
        return status.getText();
    }

    /**
     * Opens a listed usage when its document revision is still current.
     *
     * @param location listed usage to open
     * @return whether the usage was revealed
     */
    public boolean open(Location.TextLocation location) {
        Request current = request;
        if (current == null || !shown.contains(location)
            || !(current.workspace().document(location.documentId())
                .orElse(null) instanceof TextDocumentSnapshot snapshot))
            return false;

        boolean opened = Services.EDITOR_TAB_MANAGER.navigateTo(null, snapshot, location);
        if (!opened) {
            status.setText(L18n.localize("railroad.ide.usages.outdated_target"));
        }
        return opened;
    }

    /**
     * Returns a completion barrier for the running search, for deterministic tests and shutdown.
     *
     * @return completion of the currently owned request
     */
    public CompletableFuture<Void> completion() {
        return requests.completion();
    }

    @Override
    public void close() {
        if (closed)
            return;

        closed = true;
        invalidateRequest();
        requests.close();
        if (storeSubscription != null) {
            storeSubscription.close();
            storeSubscription = null;
        }
        request = null;
        clearResults();
    }

    /**
     * Starts a search, cancelling the previous one and clearing its results. Call on the JavaFX thread.
     *
     * @param request captured search inputs
     */
    public void search(Request request) {
        Objects.requireNonNull(request, "request");
        if (!Platform.isFxApplicationThread())
            throw new IllegalStateException("Usage searches must start on the FX thread");
        if (closed)
            return;

        invalidateRequest();
        clearResults();
        observe(request.store());
        this.request = request;
        if (request.query() instanceof UsageQuery.OfSymbol(SymbolId id)) {
            symbol = id;
        } else {
            symbol = null;
            symbolName = null;
        }
        state = State.SEARCHING;

        var cancellation = new CancellationTokenSource(request.providerLifetime());
        var context = new FeatureRequestContext(request.workspace(), request.source(), cancellation.token(),
            Deadline.after(SEARCH_BUDGET));
        activeCancellation = cancellation;
        activeContext = context;
        updateStatus();

        UsageSink sink = (batch, searched, total) -> {
            List<Location<?>> copy = List.copyOf(batch);
            Platform.runLater(() -> {
                if (activeContext != context || state != State.SEARCHING)
                    return;

                searchedDocuments = searched;
                totalDocuments = total;
                append(request.workspace(), copy);
                updateStatus();
            });
        };

        CompletableFuture<FeatureResult<UsageSearch>> future;
        try {
            future = requests.submit("usages", RequestPriority.NAVIGATION, context, cancellation,
                () -> ProviderExecutor.shared().execute(request.source().languageId() + ":usages", context,
                    SEARCH_BUDGET, () -> request.provider().usages(context, request.query(), sink)));
        } catch (RuntimeException exception) {
            future = CompletableFuture.failedFuture(exception);
        }

        future.thenAccept(result -> Platform.runLater(() -> {
            if (activeContext != context || result == null || !result.belongsTo(context))
                return;

            complete(request, result.value());
        })).exceptionally(throwable -> {
            Throwable cause = unwrap(throwable);
            if (cause instanceof DeadlineExceededException) {
                Railroad.LOGGER.warn("Find Usages timed out for {} in {}", request.query(), request.source().uri());
            } else if (!(cause instanceof CancellationException)) {
                Railroad.LOGGER.error("Find Usages failed for {} in {}", request.query(), request.source().uri(),
                    cause);
            }
            Platform.runLater(() -> {
                if (activeContext != context)
                    return;

                releaseRequest();
                if (cause instanceof DeadlineExceededException) {
                    state = State.TIMED_OUT;
                } else if (cause instanceof CancellationException) {
                    state = State.CANCELLED;
                } else {
                    state = State.FAILED;
                    failureMessage = cause.getMessage() == null || cause.getMessage().isBlank()
                        ? cause.getClass().getSimpleName()
                        : cause.getMessage();
                }
                updateStatus();
            });
            return null;
        });
    }

    private void complete(Request request, @Nullable UsageSearch search) {
        releaseRequest();
        if (search == null) {
            state = State.UNRESOLVED;
            clearResults();
            updateStatus();
            return;
        }

        symbol = search.symbol();
        symbolName = search.displayName();
        if (!usages().equals(search.usages())) {
            clearResults();
            append(request.workspace(), search.usages());
        }
        state = isCurrent(request) ? State.COMPLETE : State.OUTDATED;
        updateStatus();
    }

    private void append(WorkspaceSnapshot workspace, List<Location<?>> batch) {
        for (Location<?> usage : batch) {
            if (!(usage instanceof Location.TextLocation location)
                || !(workspace.document(location.documentId()).orElse(null) instanceof TextDocumentSnapshot snapshot))
                continue;

            try {
                location.validateAgainst(snapshot);
            } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
                Railroad.LOGGER.warn("Discarding usage with an invalid location", exception);
                continue;
            }

            if (!shown.add(location))
                continue;

            TreeItem<Row> group = groups.computeIfAbsent(location.documentId(), _ -> {
                var item = new TreeItem<Row>(new FileRow(snapshot, directoryLabel(snapshot)));
                item.setExpanded(true);
                root.getChildren().add(item);
                return item;
            });
            group.getChildren().add(new TreeItem<>(UsageRow.from(snapshot, location)));
        }
    }

    private void openSelected() {
        TreeItem<Row> selected = tree.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getValue() instanceof UsageRow row) {
            open(row.location());
        }
    }

    private void observe(DocumentStore store) {
        if (storeSubscription != null) {
            storeSubscription.close();
        }
        storeSubscription = store.onChange(() -> {
            if (outdatedCheckQueued.compareAndSet(false, true)) {
                Platform.runLater(this::markOutdatedIfChanged);
            }
        });
    }

    private String directoryLabel(TextDocumentSnapshot snapshot) {
        return snapshot.uri().filePath().map(Path::getParent)
            .map(parent -> projectRoot != null && parent.startsWith(projectRoot)
                ? projectRoot.relativize(parent)
                : parent)
            .map(Path::toString).orElse("");
    }

    private static boolean isCurrent(Request request) {
        WorkspaceSnapshot latest = request.store().capture();
        return latest.documents().equals(request.workspace().documents())
            && latest.configuration().equals(request.workspace().configuration());
    }

    private void invalidateRequest() {
        requests.cancel("usages");
        if (activeCancellation != null) {
            activeCancellation.cancel();
        }
        releaseRequest();
    }

    private void releaseRequest() {
        activeCancellation = null;
        activeContext = null;
    }

    private void clearResults() {
        root.getChildren().clear();
        groups.clear();
        shown.clear();
        searchedDocuments = 0;
        totalDocuments = 0;
    }

    private void markOutdatedIfChanged() {
        outdatedCheckQueued.set(false);
        Request current = request;
        if (closed || current == null || (state != State.COMPLETE && shown.isEmpty())
            || state == State.SEARCHING || state == State.OUTDATED
            || isCurrent(current))
            return;

        state = State.OUTDATED;
        updateStatus();
    }

    private void updateStatus() {
        int count = shown.size();
        String name = symbolName == null ? "" : symbolName;
        boolean searching = state == State.SEARCHING;
        boolean hasResults = count > 0;
        header.setVisible(request != null);
        tree.setVisible(hasResults);
        empty.setVisible(!hasResults);
        status.setVisible(hasResults);
        groupActions.setVisible(hasResults);
        cancel.setVisible(searching);
        refresh.setVisible(!searching);
        progress.setVisible(searching);
        pseudoClassStateChanged(WARNING, state == State.OUTDATED || state == State.TIMED_OUT);
        pseudoClassStateChanged(FAILED, state == State.FAILED);
        if (request != null) {
            String title = name.isBlank()
                ? request.source().uri().fileName().orElse(request.source().uri().toString())
                : name;
            heading.setText(title);
        }
        progress.setProgress(
            totalDocuments > 0 ? (double) searchedDocuments / totalDocuments : ProgressBar.INDETERMINATE_PROGRESS);
        status.setText(switch (state) {
            case IDLE -> L18n.localize("railroad.ide.usages.idle");
            case SEARCHING -> totalDocuments == 0
                ? L18n.localize("railroad.ide.usages.preparing")
                : L18n.localize("railroad.ide.usages.searching", searchedDocuments, fileCount(totalDocuments),
                    usageCount(count));
            case COMPLETE -> L18n.localize("railroad.ide.usages.complete", usageCount(count), fileCount(groups.size()));
            case CANCELLED -> L18n.localize("railroad.ide.usages.cancelled", usageCount(count));
            case OUTDATED -> L18n.localize("railroad.ide.usages.outdated");
            case UNRESOLVED -> L18n.localize("railroad.ide.usages.unresolved");
            case TIMED_OUT -> L18n.localize("railroad.ide.usages.timed_out", usageCount(count));
            case FAILED -> L18n.localize("railroad.ide.usages.failed", failureMessage);
        });
        if (!hasResults) {
            updateEmptyState();
        }
    }

    private void updateEmptyState() {
        emptyIcon.setIconCode(switch (state) {
            case FAILED, TIMED_OUT, OUTDATED -> FontAwesomeSolid.EXCLAMATION_CIRCLE;
            case CANCELLED -> FontAwesomeSolid.STOP_CIRCLE;
            default -> FontAwesomeSolid.SEARCH;
        });
        String titleKey = switch (state) {
            case IDLE -> "idle_title";
            case SEARCHING -> "search_title";
            case COMPLETE -> "no_results";
            case UNRESOLVED -> "unresolved";
            case CANCELLED -> "cancelled_title";
            case TIMED_OUT -> "timeout_title";
            case OUTDATED -> "outdated_title";
            case FAILED -> "failed_title";
        };
        emptyTitle.setText(L18n.localize("railroad.ide.usages." + titleKey));
        emptyDetail.setText(switch (state) {
            case IDLE, UNRESOLVED -> L18n.localize("railroad.ide.usages.idle");
            case COMPLETE -> L18n.localize("railroad.ide.usages.no_results_detail");
            case TIMED_OUT, CANCELLED, OUTDATED -> L18n.localize("railroad.ide.usages.retry");
            case SEARCHING -> status.getText();
            case FAILED -> failureMessage;
        });
    }

    /**
     * Search state presented by the pane.
     */
    public enum State {
        /** No search has been started. */
        IDLE,
        /** A search is running; results may be partial. */
        SEARCHING,
        /** The search completed against an unchanged workspace. */
        COMPLETE,
        /** The search was cancelled; results may be partial. */
        CANCELLED,
        /** The workspace changed after the results were captured. */
        OUTDATED,
        /** The query resolved to no symbol or the language cannot search for usages. */
        UNRESOLVED,
        /**
         * The search exceeded its deadline; results may be partial.
         */
        TIMED_OUT,
        /**
         * The provider failed.
         */
        FAILED
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable cause = throwable;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
            && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private sealed interface Row permits FileRow, UsageRow {
    }

    private record FileRow(TextDocumentSnapshot snapshot, String directory) implements Row {
    }

    private record UsageRow(Location.TextLocation location, String before, String match, String after) implements Row {
        private static UsageRow from(TextDocumentSnapshot snapshot, Location.TextLocation location) {
            String text = snapshot.text();
            int line = location.line();
            int lineEnd = snapshot.lineMap().lineEnd(line);
            int start = Math.min(location.range().start, lineEnd);
            int end = Math.min(location.range().end, lineEnd);
            String before = text.substring(snapshot.lineMap().lineStart(line), start).stripLeading();
            String match = text.substring(start, end);
            String after = text.substring(end, lineEnd).stripTrailing();
            return new UsageRow(location, before, match, after);
        }
    }

    private static final class RowCell extends TreeCell<Row> {
        private static final PseudoClass FILE_GROUP = PseudoClass.getPseudoClass("file-group");
        private final Label fileName = styledLabel("usages-file-name");
        private final Label directory = styledLabel("usages-directory");
        private final Label count = styledLabel("usages-file-count");
        private final HBox fileLine = new HBox(fileName, count, directory);
        private final Label position = styledLabel("usages-position");
        private final Label before = styledLabel("usages-code");
        private final Label match = styledLabel("usages-match");
        private final Label after = styledLabel("usages-code");
        private final HBox snippet = new HBox(before, match, after);
        private final HBox usageLine = new HBox(position, snippet);
        private final Tooltip tip = new Tooltip();

        private RowCell() {
            setPrefWidth(0);
            fileLine.getStyleClass().add("usages-file-line");
            fileLine.setPrefWidth(Double.MAX_VALUE);
            usageLine.getStyleClass().add("usages-usage-line");
            snippet.getStyleClass().add("usages-snippet");
            directory.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
            directory.setPrefWidth(0);
            directory.setMinWidth(0);
            HBox.setHgrow(directory, Priority.ALWAYS);
            before.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
            before.setMinWidth(0);
            after.setMinWidth(0);
            match.setMinWidth(USE_PREF_SIZE);
            count.setMinWidth(USE_PREF_SIZE);
            treeItemProperty().addListener((_, _, item) -> {
                count.textProperty().unbind();
                if (item != null && item.getValue() instanceof FileRow) {
                    count.textProperty().bind(Bindings.size(item.getChildren()).asString());
                }
            });
        }

        private static Label styledLabel(String style) {
            var label = new Label();
            label.getStyleClass().add(style);
            return label;
        }

        @Override
        protected void updateItem(Row item, boolean empty) {
            super.updateItem(item, empty);
            pseudoClassStateChanged(FILE_GROUP, !empty && item instanceof FileRow);
            if (empty || item == null) {
                setGraphic(null);
                setTooltip(null);
                setAccessibleText(null);
                return;
            }
            if (item instanceof FileRow file) {
                var uri = file.snapshot().uri();
                fileName.setText(uri.fileName().orElse(uri.toString()));
                fileName.setGraphic(
                    uri.filePath().map(FileUtils::getIcon).orElseGet(() -> new FontIcon(FontAwesomeSolid.FILE)));
                directory.setText(file.directory());
                tip.setText(uri.filePath().map(Path::toString).orElse(uri.toString()));
                setAccessibleText(fileName.getText() + " " + file.directory());
                setGraphic(fileLine);
            } else if (item instanceof UsageRow usage) {
                position.setText((usage.location().line() + 1) + ":" + (usage.location().column() + 1));
                before.setText(usage.before());
                match.setText(usage.match());
                after.setText(usage.after());
                String description = position.getText() + "  " + usage.before() + usage.match() + usage.after();
                setAccessibleText(description);
                tip.setText(description);
                setGraphic(usageLine);
            }
            setTooltip(tip);
        }
    }
}
