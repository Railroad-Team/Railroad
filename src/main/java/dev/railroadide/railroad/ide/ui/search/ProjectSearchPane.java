package dev.railroadide.railroad.ide.ui.search;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.edit.WorkspaceEditService;
import dev.railroadide.railroad.ide.search.FileMask;
import dev.railroadide.railroad.ide.search.ProjectTextSearch;
import dev.railroadide.railroad.ide.search.ProjectTextSearch.FileMatches;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEdit;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEditProblem;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEditResult;
import dev.railroadide.railroad.ide.ui.codeeditor.find.FindQuery;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.localized.LocalizedTextProperty;
import dev.railroadide.railroad.ui.localized.LocalizedTooltip;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.utility.FileUtils;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.jspecify.annotations.Nullable;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.PatternSyntaxException;

/**
 * Searches the text of every project file and replaces matches across files, with results grouped by file and an
 * inline preview of each replacement.
 * <p>
 * All state is owned by the JavaFX thread. Searches run against one capture of the project's document store, so
 * they include unsaved editor content, and a newer search or closing the pane cancels the running one. Listed
 * matches stay bound to the captured revisions: once the workspace changes the results are marked outdated, and a
 * replacement is applied through the project's {@link WorkspaceEditService} as one transaction that is refused if
 * any affected document changed since the search.
 */
public final class ProjectSearchPane extends BorderPane implements AutoCloseable {
    /**
     * Maximum number of matches one search lists.
     */
    public static final int MATCH_LIMIT = 10_000;
    private static final int MAX_SEED_LENGTH = 1_000;
    private static final Duration SEARCH_DELAY = Duration.millis(200);
    private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");
    private static final PseudoClass FAILED = PseudoClass.getPseudoClass("failed");
    private static final ExecutorService SEARCHES = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "Railroad project search");
        thread.setDaemon(true);
        return thread;
    });

    private final Path root;
    private final Supplier<Optional<DocumentStore>> stores;
    private final Supplier<Optional<WorkspaceEditService>> edits;
    private final TextField searchField = new TextField();
    private final TextField replaceField = new TextField();
    private final TextField maskField = new TextField();
    private final ToggleButton matchCase = option("Cc", "railroad.editor.find.match_case");
    private final ToggleButton wholeWords = option("W", "railroad.editor.find.whole_words");
    private final ToggleButton regex = option(".*", "railroad.editor.find.regex");
    private final RRButton replaceToggle = toolButton("railroad.editor.find.toggle_replace",
        FontAwesomeSolid.CHEVRON_RIGHT);
    private final RRButton replaceAllButton = new RRButton("railroad.editor.find.replace_all");
    private final HBox replaceRow;
    private final Label confirmMessage = new Label();
    private final HBox confirmBar;
    private final Label status = new Label();
    private final ProgressBar progress = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
    private final HBox groupActions = new HBox();
    private final FontIcon emptyIcon = new FontIcon();
    private final Label emptyTitle = new Label();
    private final Label emptyDetail = new Label();
    private final VBox empty = new VBox(emptyIcon, emptyTitle, emptyDetail);
    private final TreeItem<Row> resultRoot = new TreeItem<>();
    private final TreeView<Row> tree = new TreeView<>(resultRoot);
    private final Map<DocumentId, TreeItem<Row>> groups = new LinkedHashMap<>();
    private final PauseTransition searchDelay = new PauseTransition(SEARCH_DELAY);
    private final Queue<FileMatches> pendingFiles = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean flushQueued = new AtomicBoolean();
    private final AtomicBoolean outdatedCheckQueued = new AtomicBoolean();

    private @Nullable FindQuery query;
    private @Nullable WorkspaceSnapshot searched;
    private @Nullable AtomicBoolean activeCancellation;
    private DocumentStore.@Nullable ChangeSubscription storeSubscription;
    private @Nullable DocumentStore observedStore;
    private @Nullable String replacementError;
    private @Nullable String notice;
    private String failureMessage = "";
    private State state = State.IDLE;
    private int matchCount;
    private boolean limitReached;
    private boolean closed;

    /**
     * Creates the pane for the open project's document store and workspace edits.
     *
     * @param project project whose files are searched
     */
    public ProjectSearchPane(Project project) {
        this(project.getPath(), Services.IDE_STATE::getDocumentStore, Services.IDE_STATE::getWorkspaceEdits);
    }

    /**
     * Creates a pane over explicit services.
     *
     * @param root project directory; only files inside it are searched
     * @param stores supplies the project's document store when a search starts
     * @param edits supplies the project's workspace-edit service when replacing
     */
    public ProjectSearchPane(
        Path root,
        Supplier<Optional<DocumentStore>> stores,
        Supplier<Optional<WorkspaceEditService>> edits
    ) {
        this.root = root.toAbsolutePath().normalize();
        this.stores = Objects.requireNonNull(stores, "stores");
        this.edits = Objects.requireNonNull(edits, "edits");
        getStyleClass().add("project-search-pane");

        searchField.getStyleClass().add("project-search-field");
        searchField.promptTextProperty().bind(new LocalizedTextProperty(searchField, "promptText",
            "railroad.ide.find_in_files.search_prompt"));
        replaceField.getStyleClass().add("project-search-field");
        replaceField.promptTextProperty().bind(new LocalizedTextProperty(replaceField, "promptText",
            "railroad.ide.find_in_files.replace_prompt"));
        maskField.getStyleClass().add("project-search-mask");
        maskField.promptTextProperty().bind(new LocalizedTextProperty(maskField, "promptText",
            "railroad.ide.find_in_files.mask_prompt"));
        maskField.setTooltip(new LocalizedTooltip("railroad.ide.find_in_files.mask_tooltip"));
        HBox.setHgrow(searchField, Priority.ALWAYS);
        HBox.setHgrow(replaceField, Priority.ALWAYS);

        var options = new HBox(matchCase, wholeWords, regex);
        options.getStyleClass().add("find-replace-options");
        options.setAlignment(Pos.CENTER_LEFT);
        var searchRow = new HBox(replaceToggle, searchField, options, maskField);
        searchRow.getStyleClass().add("project-search-row");
        searchRow.setAlignment(Pos.CENTER_LEFT);

        replaceAllButton.setVariant(ButtonVariant.SECONDARY);
        replaceAllButton.setOnAction(_ -> requestReplaceAll());
        var replaceIndent = new Region();
        replaceIndent.minWidthProperty().bind(replaceToggle.widthProperty());
        replaceRow = new HBox(replaceIndent, replaceField, replaceAllButton);
        replaceRow.getStyleClass().add("project-search-row");
        replaceRow.setAlignment(Pos.CENTER_LEFT);

        var confirm = new RRButton("railroad.ide.find_in_files.confirm_replace");
        confirm.setVariant(ButtonVariant.PRIMARY);
        confirm.setOnAction(_ -> replaceAll());
        var dismiss = new RRButton("railroad.generic.cancel");
        dismiss.setVariant(ButtonVariant.SECONDARY);
        dismiss.setOnAction(_ -> hideConfirmation());
        confirmMessage.setWrapText(true);
        confirmMessage.setMinWidth(0);
        HBox.setHgrow(confirmMessage, Priority.ALWAYS);
        confirmBar = new HBox(confirmMessage, confirm, dismiss);
        confirmBar.getStyleClass().add("project-search-confirm");
        confirmBar.setAlignment(Pos.CENTER_LEFT);

        RRButton collapse = toolButton("railroad.generic.collapse_all", FontAwesomeSolid.COMPRESS_ALT);
        collapse.setOnAction(_ -> groups.values().forEach(item -> item.setExpanded(false)));
        RRButton expand = toolButton("railroad.generic.expand_all", FontAwesomeSolid.EXPAND_ALT);
        expand.setOnAction(_ -> groups.values().forEach(item -> item.setExpanded(true)));
        RRButton refresh = toolButton("railroad.ide.find_in_files.refresh", FontAwesomeSolid.SYNC_ALT);
        refresh.setOnAction(_ -> search());
        groupActions.getChildren().addAll(collapse, expand);
        groupActions.setAlignment(Pos.CENTER_LEFT);
        status.getStyleClass().add("usages-summary");
        status.setMinWidth(0);
        var statusTooltip = new Tooltip();
        statusTooltip.textProperty().bind(status.textProperty());
        status.setTooltip(statusTooltip);
        HBox.setHgrow(status, Priority.ALWAYS);
        status.setMaxWidth(Double.MAX_VALUE);
        var statusRow = new HBox(status, groupActions, refresh);
        statusRow.getStyleClass().add("project-search-status-row");
        statusRow.setAlignment(Pos.CENTER_LEFT);

        progress.getStyleClass().add("usages-progress");
        progress.setMaxWidth(Double.MAX_VALUE);
        var header = new VBox(searchRow, replaceRow, confirmBar, statusRow, progress);
        header.getStyleClass().add("project-search-header");
        setTop(header);

        tree.getStyleClass().addAll("rr-tree-view", "usages-tree", "project-search-tree");
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
            } else if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
                excludeSelected();
                event.consume();
            }
        });

        var openItem = new MenuItem(L18n.localize("railroad.ide.find_in_files.open"));
        openItem.setOnAction(_ -> openSelected());
        var excludeItem = new MenuItem(L18n.localize("railroad.ide.find_in_files.exclude"));
        excludeItem.setOnAction(_ -> excludeSelected());
        tree.setContextMenu(new ContextMenu(openItem, excludeItem));

        empty.getStyleClass().add("usages-empty");
        empty.setMaxSize(460, USE_PREF_SIZE);
        emptyTitle.getStyleClass().add("usages-empty-title");
        emptyDetail.getStyleClass().add("usages-empty-detail");
        emptyTitle.setWrapText(true);
        emptyDetail.setWrapText(true);
        setCenter(new StackPane(tree, empty));
        for (Region node : List.of(replaceRow, confirmBar, progress, groupActions, tree, empty)) {
            node.managedProperty().bind(node.visibleProperty());
        }

        replaceToggle.setOnAction(_ -> setReplaceVisible(!isReplaceVisible()));
        searchDelay.setOnFinished(_ -> search());
        searchField.textProperty().addListener(_ -> queryChanged());
        maskField.textProperty().addListener(_ -> queryChanged());
        matchCase.selectedProperty().addListener(_ -> queryChanged());
        wholeWords.selectedProperty().addListener(_ -> queryChanged());
        regex.selectedProperty().addListener(_ -> queryChanged());
        replaceField.textProperty().addListener(_ -> replacementChanged());
        for (TextField field : List.of(searchField, maskField)) {
            field.setOnAction(_ -> search());
        }

        replaceField.setOnAction(_ -> requestReplaceAll());
        addEventFilter(KeyEvent.KEY_PRESSED, this::handleOptionShortcut);
        setReplaceVisible(false);
        updateStatus();
    }

    /**
     * Shows the search fields, optionally seeding the query and revealing the replace row, and focuses the query.
     *
     * @param seed text to search for, such as an editor selection; ignored when blank or spanning several lines
     * @param withReplace whether the replace row is shown
     */
    public void open(@Nullable String seed, boolean withReplace) {
        setReplaceVisible(withReplace);
        if (seed != null && !seed.isBlank() && seed.length() <= MAX_SEED_LENGTH && seed.indexOf('\n') < 0) {
            searchField.setText(regex.isSelected() ? escapeRegex(seed) : seed);
            search();
        }

        Platform.runLater(() -> {
            searchField.requestFocus();
            searchField.selectAll();
        });
    }

    /**
     * Starts a search with the current fields immediately, cancelling the running one.
     */
    public void search() {
        if (!Platform.isFxApplicationThread())
            throw new IllegalStateException("Project searches must start on the FX thread");

        if (closed)
            return;

        searchDelay.stop();
        cancelRunningSearch();
        clearResults();
        hideConfirmation();
        replacementError = null;
        var next = new FindQuery(searchField.getText(), matchCase.isSelected(), wholeWords.isSelected(),
            regex.isSelected());
        if (next.isEmpty()) {
            query = null;
            searched = null;
            state = State.IDLE;
            updateStatus();
            return;
        }

        try {
            next.compile();
        } catch (PatternSyntaxException exception) {
            query = null;
            searched = null;
            state = State.INVALID;
            failureMessage = exception.getDescription();
            updateStatus();
            return;
        }

        Optional<DocumentStore> store = stores.get();
        if (store.isEmpty()) {
            query = null;
            searched = null;
            state = State.FAILED;
            failureMessage = L18n.localize("railroad.ide.find_in_files.no_project");
            updateStatus();
            return;
        }

        observe(store.get());
        WorkspaceSnapshot workspace = store.get().capture();
        var mask = new FileMask(maskField.getText());
        var cancellation = new AtomicBoolean();
        activeCancellation = cancellation;
        query = next;
        searched = workspace;
        state = State.SEARCHING;
        updateStatus();

        CompletableFuture.supplyAsync(() -> ProjectTextSearch.search(workspace, root, next, mask, MATCH_LIMIT,
            cancellation::get, file -> {
                pendingFiles.add(file);
                if (flushQueued.compareAndSet(false, true)) {
                    Platform.runLater(() -> flush(cancellation));
                }
            }), SEARCHES).whenComplete((result, throwable) -> Platform.runLater(() -> {
                if (activeCancellation != cancellation)
                    return;

                flush(cancellation);
                activeCancellation = null;
                Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                    ? throwable.getCause()
                    : throwable;
                if (cause == null) {
                    limitReached = result.limitReached();
                    state = isCurrent(workspace) ? State.COMPLETE : State.OUTDATED;
                } else if (cause instanceof CancellationException) {
                    state = State.IDLE;
                } else {
                    Railroad.LOGGER.error("Project search failed for {}", next, cause);
                    state = State.FAILED;
                    failureMessage = cause.getMessage() == null || cause.getMessage().isBlank()
                        ? cause.getClass().getSimpleName()
                        : cause.getMessage();
                }

                validateReplacement();
                updateStatus();
            }));
    }

    /**
     * Shows the confirmation for replacing every listed match, or reports why nothing can be replaced.
     */
    public void requestReplaceAll() {
        if (!isReplaceVisible() || state == State.SEARCHING || matchCount == 0)
            return;

        validateReplacement();
        if (replacementError != null) {
            updateStatus();
            return;
        }

        confirmMessage.setText(L18n.localize("railroad.ide.find_in_files.confirm_message", matchText(matchCount),
            fileText(groups.size()), replaceField.getText()));
        confirmBar.setVisible(true);
    }

    /**
     * Replaces every listed match through the project's workspace-edit service as one transaction, then searches
     * again. Nothing is replaced if any affected file changed since the search.
     *
     * @return outcome, or an empty optional when nothing could be attempted
     */
    public Optional<WorkspaceEditResult> replaceAll() {
        hideConfirmation();
        FindQuery current = query;
        if (current == null || state == State.SEARCHING || matchCount == 0)
            return Optional.empty();

        Optional<WorkspaceEditService> service = edits.get();
        if (service.isEmpty()) {
            notice = L18n.localize("railroad.ide.find_in_files.no_project");
            updateStatus();
            return Optional.empty();
        }

        WorkspaceEdit edit;
        try {
            edit = ProjectTextSearch.replacementEdit(L18n.localize("railroad.ide.find_in_files.edit_label"),
                results(), current, replaceField.getText());
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            replacementError = exception.getMessage();
            updateStatus();
            return Optional.empty();
        }

        int replacedMatches = matchCount;
        int replacedFiles = groups.size();
        WorkspaceEditResult result = service.get().apply(edit);
        if (result.applied()) {
            search();
            notice = L18n.localize("railroad.ide.find_in_files.replaced", matchText(replacedMatches),
                fileText(replacedFiles));
        } else {
            notice = L18n.localize("railroad.ide.find_in_files.replace_failed", result.problems().stream()
                .findFirst().map(WorkspaceEditProblem::message).orElse(result.status().toString()));
            if (state == State.COMPLETE && searched != null && !isCurrent(searched)) {
                state = State.OUTDATED;
            }
        }

        updateStatus();
        return Optional.of(result);
    }

    /**
     * Returns the listed matches, grouped by file in display order, without excluded matches.
     *
     * @return listed files and their matches
     */
    public List<FileMatches> results() {
        List<FileMatches> files = new ArrayList<>();
        for (TreeItem<Row> group : resultRoot.getChildren()) {
            if (group.getValue() instanceof FileRow file) {
                List<TextRange> ranges = group.getChildren().stream()
                    .map(TreeItem::getValue)
                    .map(row -> ((MatchRow) row).range())
                    .toList();
                files.add(new FileMatches(file.matches().document(), file.matches().relativePath(), ranges));
            }
        }

        return files;
    }

    /**
     * Returns the replacement previewed for a listed match.
     *
     * @param document document containing the match
     * @param range listed match range
     * @return previewed replacement, or an empty optional when the replace row is hidden, the replacement is
     *         invalid or the match is not listed
     */
    public Optional<String> previewReplacement(DocumentId document, TextRange range) {
        TreeItem<Row> group = groups.get(document);
        return group != null && group.getValue() instanceof FileRow file ? preview(file, range) : Optional.empty();
    }

    /**
     * Removes a listed match from the results, so that it is not replaced. A file without remaining matches is
     * removed as well.
     *
     * @param document document containing the match
     * @param range listed match range
     * @return whether the match was listed
     */
    public boolean exclude(DocumentId document, TextRange range) {
        TreeItem<Row> group = groups.get(document);
        if (group == null)
            return false;

        for (TreeItem<Row> item : group.getChildren()) {
            if (item.getValue() instanceof MatchRow match && match.range().equals(range)) {
                exclude(item);
                return true;
            }
        }

        return false;
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
     * Returns the user-facing status line.
     *
     * @return status text
     */
    public String statusText() {
        return status.getText();
    }

    /**
     * Returns the field holding the search text.
     *
     * @return query field
     */
    public TextField searchField() {
        return searchField;
    }

    /**
     * Returns the field holding the replacement template.
     *
     * @return replacement field
     */
    public TextField replaceField() {
        return replaceField;
    }

    /**
     * Returns the field holding the file mask.
     *
     * @return file mask field
     */
    public TextField maskField() {
        return maskField;
    }

    /**
     * Returns the toggle that makes matching case-sensitive.
     *
     * @return match-case toggle
     */
    public ToggleButton matchCaseToggle() {
        return matchCase;
    }

    /**
     * Returns the toggle that restricts matches to whole words.
     *
     * @return whole-words toggle
     */
    public ToggleButton wholeWordsToggle() {
        return wholeWords;
    }

    /**
     * Returns the toggle that interprets the search text as a regular expression.
     *
     * @return regex toggle
     */
    public ToggleButton regexToggle() {
        return regex;
    }

    /**
     * Reports whether the replace row is shown.
     *
     * @return whether replacements are previewed
     */
    public boolean isReplaceVisible() {
        return replaceRow.isVisible();
    }

    /**
     * Reports whether the replace-all confirmation is shown.
     *
     * @return whether a replacement awaits confirmation
     */
    public boolean isConfirmationVisible() {
        return confirmBar.isVisible();
    }

    @Override
    public void close() {
        if (closed)
            return;

        closed = true;
        searchDelay.stop();
        cancelRunningSearch();
        if (storeSubscription != null) {
            storeSubscription.close();
            storeSubscription = null;
        }

        observedStore = null;
        clearResults();
    }

    private void setReplaceVisible(boolean visible) {
        replaceRow.setVisible(visible);
        replaceToggle
            .setGraphic(new FontIcon(visible ? FontAwesomeSolid.CHEVRON_DOWN : FontAwesomeSolid.CHEVRON_RIGHT));
        if (!visible) {
            hideConfirmation();
        }

        replacementChanged();
    }

    private void queryChanged() {
        notice = null;
        searchDelay.playFromStart();
    }

    private void replacementChanged() {
        hideConfirmation();
        notice = null;
        for (TreeItem<Row> group : groups.values()) {
            if (group.getValue() instanceof FileRow file) {
                file.clearPreview();
            }
        }

        validateReplacement();
        tree.refresh();
        updateStatus();
    }

    private void validateReplacement() {
        replacementError = null;
        FindQuery current = query;
        if (!isReplaceVisible() || current == null || resultRoot.getChildren().isEmpty())
            return;

        FileRow first = (FileRow) resultRoot.getChildren().getFirst().getValue();
        try {
            ProjectTextSearch.replacements(first.matches(), current, replaceField.getText());
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            replacementError = exception.getMessage();
        }
    }

    private Optional<String> preview(FileRow file, TextRange range) {
        FindQuery current = query;
        if (!isReplaceVisible() || current == null || replacementError != null)
            return Optional.empty();

        try {
            return Optional.ofNullable(file.preview(current, replaceField.getText()).get(range));
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            return Optional.empty();
        }
    }

    private void flush(AtomicBoolean cancellation) {
        flushQueued.set(false);
        if (activeCancellation != cancellation) {
            pendingFiles.clear();
            return;
        }

        boolean added = false;
        for (FileMatches file = pendingFiles.poll(); file != null; file = pendingFiles.poll()) {
            append(file);
            added = true;
        }

        if (added) {
            updateStatus();
        }
    }

    private void append(FileMatches file) {
        TextDocumentSnapshot snapshot = file.document();
        var fileRow = new FileRow(file, directoryLabel(file.relativePath()));
        var group = new TreeItem<Row>(fileRow);
        group.setExpanded(true);
        for (TextRange range : file.matches()) {
            group.getChildren().add(new TreeItem<>(MatchRow.from(fileRow, snapshot, range)));
        }

        matchCount += file.matches().size();
        groups.put(snapshot.id(), group);
        resultRoot.getChildren().add(group);
    }

    private void openSelected() {
        TreeItem<Row> selected = tree.getSelectionModel().getSelectedItem();
        if (selected == null)
            return;

        MatchRow match = switch (selected.getValue()) {
            case MatchRow row -> row;
            case FileRow _ -> selected.getChildren().isEmpty()
                ? null
                : (MatchRow) selected.getChildren().getFirst().getValue();
        };

        if (match == null)
            return;

        TextDocumentSnapshot snapshot = match.file().matches().document();
        Location.TextLocation location = Location.TextLocation.from(snapshot, match.range().start, match.range().end);
        if (!Services.EDITOR_TAB_MANAGER.navigateTo(null, snapshot, location)) {
            notice = L18n.localize("railroad.ide.find_in_files.outdated_target");
            updateStatus();
        }
    }

    private void excludeSelected() {
        TreeItem<Row> selected = tree.getSelectionModel().getSelectedItem();
        if (selected != null) {
            exclude(selected);
        }
    }

    private void exclude(TreeItem<Row> item) {
        hideConfirmation();
        TreeItem<Row> group = item.getValue() instanceof FileRow ? item : item.getParent();
        if (item.getValue() instanceof FileRow) {
            matchCount -= item.getChildren().size();
        } else {
            matchCount--;
            group.getChildren().remove(item);
        }

        if (item == group || group.getChildren().isEmpty()) {
            resultRoot.getChildren().remove(group);
            groups.remove(((FileRow) group.getValue()).matches().document().id());
        }

        updateStatus();
    }

    private void hideConfirmation() {
        confirmBar.setVisible(false);
    }

    private void observe(DocumentStore store) {
        if (observedStore == store)
            return;

        if (storeSubscription != null) {
            storeSubscription.close();
        }

        observedStore = store;
        storeSubscription = store.onChange(() -> {
            if (outdatedCheckQueued.compareAndSet(false, true)) {
                Platform.runLater(this::markOutdatedIfChanged);
            }
        });
    }

    private void markOutdatedIfChanged() {
        outdatedCheckQueued.set(false);
        WorkspaceSnapshot current = searched;
        if (closed || current == null || state != State.COMPLETE || isCurrent(current))
            return;

        state = State.OUTDATED;
        updateStatus();
    }

    private boolean isCurrent(WorkspaceSnapshot workspace) {
        DocumentStore store = observedStore;
        return store != null && store.capture().documents().equals(workspace.documents());
    }

    private void cancelRunningSearch() {
        if (activeCancellation != null) {
            activeCancellation.set(true);
            activeCancellation = null;
        }

        pendingFiles.clear();
    }

    private void clearResults() {
        resultRoot.getChildren().clear();
        groups.clear();
        matchCount = 0;
        limitReached = false;
    }

    private String directoryLabel(String relativePath) {
        int separator = relativePath.lastIndexOf('/');
        return separator < 0 ? "" : relativePath.substring(0, separator);
    }

    private void updateStatus() {
        boolean hasResults = matchCount > 0;
        boolean searching = state == State.SEARCHING;
        tree.setVisible(hasResults);
        empty.setVisible(!hasResults);
        groupActions.setVisible(hasResults);
        progress.setVisible(searching);
        replaceAllButton.setDisable(searching || !hasResults || replacementError != null);
        pseudoClassStateChanged(WARNING, state == State.OUTDATED || limitReached && state == State.COMPLETE);
        pseudoClassStateChanged(FAILED, state == State.INVALID || state == State.FAILED
            || replacementError != null && isReplaceVisible());

        String summary = L18n.localize("railroad.ide.find_in_files.summary", matchText(matchCount),
            fileText(groups.size()));
        String text = switch (state) {
            case IDLE -> "";
            case SEARCHING -> L18n.localize("railroad.ide.find_in_files.searching", summary);
            case COMPLETE -> limitReached
                ? L18n.localize("railroad.ide.find_in_files.limit_reached", summary)
                : summary;
            case OUTDATED -> L18n.localize("railroad.ide.find_in_files.outdated");
            case INVALID -> L18n.localize("railroad.ide.find_in_files.invalid_regex", failureMessage);
            case FAILED -> L18n.localize("railroad.ide.find_in_files.failed", failureMessage);
        };

        if (replacementError != null && isReplaceVisible() && state != State.INVALID) {
            text = L18n.localize("railroad.editor.find.invalid_replacement", replacementError);
        } else if (notice != null) {
            text = text.isEmpty() ? notice : notice + " · " + text;
        }

        status.setText(text);
        if (!hasResults) {
            updateEmptyState();
        }
    }

    private void updateEmptyState() {
        emptyIcon.setIconCode(switch (state) {
            case INVALID, FAILED, OUTDATED -> FontAwesomeSolid.EXCLAMATION_CIRCLE;
            default -> FontAwesomeSolid.SEARCH;
        });

        String key = switch (state) {
            case IDLE -> "idle";
            case SEARCHING -> "searching_title";
            case COMPLETE -> "no_results";
            case OUTDATED -> "outdated_title";
            case INVALID -> "invalid_title";
            case FAILED -> "failed_title";
        };

        emptyTitle.setText(L18n.localize("railroad.ide.find_in_files." + key));
        emptyDetail.setText(switch (state) {
            case IDLE -> L18n.localize("railroad.ide.find_in_files.idle_detail");
            case COMPLETE -> L18n.localize("railroad.ide.find_in_files.no_results_detail");
            case SEARCHING, OUTDATED, INVALID, FAILED -> status.getText();
        });
    }

    private void handleOptionShortcut(KeyEvent event) {
        if (!event.isAltDown() || event.isControlDown() || event.isMetaDown() || event.isShiftDown())
            return;

        ToggleButton option = switch (event.getCode()) {
            case C -> matchCase;
            case W -> wholeWords;
            case X -> regex;
            default -> null;
        };

        if (option != null) {
            option.setSelected(!option.isSelected());
            event.consume();
        }
    }

    private static String matchText(int count) {
        return L18n.localize(count == 1
            ? "railroad.ide.find_in_files.one_match"
            : "railroad.ide.find_in_files.match_count", count);
    }

    private static String fileText(int count) {
        return L18n.localize(count == 1 ? "railroad.ide.usages.one_file" : "railroad.ide.usages.file_count", count);
    }

    private static String escapeRegex(String text) {
        return text.replaceAll("[\\\\^$.|?*+()\\[\\]{}]", "\\\\$0");
    }

    private static ToggleButton option(String text, String tooltipKey) {
        var toggle = new ToggleButton(text);
        toggle.getStyleClass().add("find-replace-option");
        toggle.setFocusTraversable(false);
        var tooltip = new LocalizedTooltip(tooltipKey);
        toggle.setTooltip(tooltip);
        toggle.accessibleTextProperty().bind(tooltip.textProperty());
        return toggle;
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

    /**
     * Search state presented by the pane.
     */
    public enum State {
        /**
         * No query has been entered.
         */
        IDLE,
        /**
         * A search is running; results may be partial.
         */
        SEARCHING,
        /**
         * The search completed against an unchanged workspace.
         */
        COMPLETE,
        /**
         * The workspace changed after the results were captured.
         */
        OUTDATED,
        /**
         * The query is not a valid regular expression.
         */
        INVALID,
        /**
         * The search could not run or failed.
         */
        FAILED
    }

    private sealed interface Row permits FileRow, MatchRow {
    }

    private static final class FileRow implements Row {
        private final FileMatches matches;
        private final String directory;
        private @Nullable String previewTemplate;
        private @Nullable FindQuery previewQuery;
        private Map<TextRange, String> previews = Map.of();

        private FileRow(FileMatches matches, String directory) {
            this.matches = matches;
            this.directory = directory;
        }

        private FileMatches matches() {
            return matches;
        }

        private Map<TextRange, String> preview(FindQuery query, String template) {
            if (!template.equals(previewTemplate) || !query.equals(previewQuery)) {
                previews = ProjectTextSearch.replacements(matches, query, template);
                previewTemplate = template;
                previewQuery = query;
            }

            return previews;
        }

        private void clearPreview() {
            previewTemplate = null;
            previewQuery = null;
            previews = Map.of();
        }
    }

    private record MatchRow(
        FileRow file,
        TextRange range,
        int line,
        int column,
        String before,
        String match,
        String after
    ) implements Row {
        private static final int MAX_CONTEXT = 200;

        private static MatchRow from(FileRow file, TextDocumentSnapshot snapshot, TextRange range) {
            String text = snapshot.text();
            LineMap lines = snapshot.lineMap();
            TextPosition position = lines.positionAt(range.start);
            int line = position.line();
            int lineStart = lines.lineStart(line);
            int lineEnd = lines.lineEnd(line);
            int end = Math.min(range.end, lineEnd);
            String before = text.substring(Math.max(lineStart, range.start - MAX_CONTEXT), range.start)
                .stripLeading();
            String match = text.substring(range.start, end);
            String after = text.substring(end, Math.min(lineEnd, end + MAX_CONTEXT)).stripTrailing();
            return new MatchRow(file, range, line, position.column(), before, match, after);
        }
    }

    private final class RowCell extends TreeCell<Row> {
        private static final PseudoClass FILE_GROUP = PseudoClass.getPseudoClass("file-group");
        private static final PseudoClass REPLACING = PseudoClass.getPseudoClass("replacing");
        private final Label fileName = styledLabel("usages-file-name");
        private final Label directory = styledLabel("usages-directory");
        private final Label count = styledLabel("usages-file-count");
        private final HBox fileLine = new HBox(fileName, count, directory);
        private final Label position = styledLabel("usages-position");
        private final Label before = styledLabel("usages-code");
        private final Label match = styledLabel("usages-match");
        private final Label replacement = styledLabel("project-search-replacement");
        private final Label after = styledLabel("usages-code");
        private final HBox snippet = new HBox(before, match, replacement, after);
        private final HBox matchLine = new HBox(position, snippet);
        private final Tooltip tip = new Tooltip();

        private RowCell() {
            setPrefWidth(0);
            fileLine.getStyleClass().add("usages-file-line");
            fileLine.setPrefWidth(Double.MAX_VALUE);
            matchLine.getStyleClass().add("usages-usage-line");
            snippet.getStyleClass().add("usages-snippet");
            directory.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
            directory.setPrefWidth(0);
            directory.setMinWidth(0);
            HBox.setHgrow(directory, Priority.ALWAYS);
            before.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
            before.setMinWidth(0);
            after.setMinWidth(0);
            match.setMinWidth(USE_PREF_SIZE);
            replacement.setMinWidth(USE_PREF_SIZE);
            replacement.managedProperty().bind(replacement.visibleProperty());
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
                pseudoClassStateChanged(REPLACING, false);
                setGraphic(null);
                setTooltip(null);
                setAccessibleText(null);
                return;
            }

            switch (item) {
                case FileRow file -> {
                    pseudoClassStateChanged(REPLACING, false);
                    DocumentUri uri = file.matches().document().uri();
                    fileName.setText(uri.fileName().orElse(file.matches().relativePath()));
                    fileName.setGraphic(
                        uri.filePath().map(FileUtils::getIcon).orElseGet(() -> new FontIcon(FontAwesomeSolid.FILE)));
                    directory.setText(file.directory);
                    tip.setText(file.matches().relativePath());
                    setAccessibleText(fileName.getText() + " " + file.directory);
                    setGraphic(fileLine);
                }

                case MatchRow row -> {
                    Optional<String> replaced = preview(row.file(), row.range());
                    pseudoClassStateChanged(REPLACING, replaced.isPresent());
                    position.setText((row.line() + 1) + ":" + (row.column() + 1));
                    before.setText(row.before());
                    match.setText(row.match());
                    replacement.setText(replaced.orElse(""));
                    replacement.setVisible(replaced.isPresent());
                    after.setText(row.after());
                    String description = position.getText() + "  " + row.before() + row.match()
                        + replaced.map(text -> " → " + text).orElse("") + row.after();
                    setAccessibleText(description);
                    tip.setText(description);
                    setGraphic(matchLine);
                }
            }

            setTooltip(tip);
        }
    }
}
