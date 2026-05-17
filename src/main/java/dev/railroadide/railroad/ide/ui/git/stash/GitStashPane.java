package dev.railroadide.railroad.ide.ui.git.stash;

import dev.railroadide.railroad.ide.IDESetup;
import dev.railroadide.railroad.ide.ui.git.commit.changes.*;
import dev.railroadide.railroad.ide.ui.git.diff.GitDiffPane;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.*;
import dev.railroadide.railroad.ui.localized.LocalizedText;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.utility.TimeFormatter;
import dev.railroadide.railroad.vcs.git.GitManager;
import dev.railroadide.railroad.vcs.git.stash.GitStashEntry;
import dev.railroadide.railroad.vcs.git.status.GitFileChange;
import dev.railroadide.railroad.vcs.git.util.GitRepository;
import dev.railroadide.railroad.window.DialogBuilder;
import dev.railroadide.railroad.window.WindowBuilder;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.Duration;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class GitStashPane extends RRVBox {
    private final Project project;
    private final GitManager gitManager;
    private final RRListView<GitStashEntry> stashesList;
    private final RRCheckBoxTreeView<ChangeItem> stashChangesTree;
    private final RRTextField messageField;
    private final RRCheckBox includeUntrackedCheckBox;
    private final RRButton createButton;
    private final RRButton applyButton;
    private final RRButton popButton;
    private final RRButton dropButton;
    private final RRButton refreshButton;
    private final LongProperty elapsedTick = new SimpleLongProperty();
    private final Timeline elapsedTimeline = new Timeline(
        new KeyFrame(Duration.seconds(1), $ -> elapsedTick.set(elapsedTick.get() + 1))
    );
    private String selectedStashRef;

    public GitStashPane(Project project) {
        this.project = project;
        this.gitManager = project.getGitManager();
        getStyleClass().add("git-stash-pane");

        var controlsRow = new RRHBox();
        controlsRow.getStyleClass().add("git-stash-controls");
        controlsRow.setAlignment(Pos.CENTER_LEFT);

        messageField = new RRTextField("railroad.git.stash.message.placeholder");
        messageField.getStyleClass().add("git-stash-message-field");
        messageField.setText("Railroad: stash changes");
        HBox.setHgrow(messageField, Priority.ALWAYS);

        includeUntrackedCheckBox = new RRCheckBox("railroad.git.stash.include_untracked");
        includeUntrackedCheckBox.getStyleClass().add("git-stash-include-untracked");

        createButton = new RRButton("railroad.git.stash.actions.create", FontAwesomeSolid.PLUS);
        createButton.setVariant(ButtonVariant.SUCCESS);
        createButton.getStyleClass().add("git-stash-create-button");

        refreshButton = new RRButton("railroad.git.stash.actions.refresh", FontAwesomeSolid.SYNC);
        refreshButton.setVariant(ButtonVariant.SECONDARY);
        refreshButton.getStyleClass().add("git-stash-refresh-button");

        controlsRow.getChildren().addAll(messageField, includeUntrackedCheckBox, createButton, refreshButton);

        stashesList = new RRListView<>();
        stashesList.getStyleClass().add("git-stash-list");
        stashesList.setCellFactory(ignored -> new GitStashEntryCell(elapsedTick));
        stashesList.setItems(FXCollections.observableArrayList());
        stashesList.setPlaceholder(new LocalizedText("railroad.git.stash.list.empty"));
        VBox.setVgrow(stashesList, Priority.ALWAYS);

        stashChangesTree = new RRCheckBoxTreeView<>();
        stashChangesTree.getStyleClass().add("git-stash-changes-tree");
        stashChangesTree.setShowRoot(false);
        stashChangesTree.setCellFactory(ignored -> new CommitChangeTreeCell());
        VBox.setVgrow(stashChangesTree, Priority.SOMETIMES);
        clearStashChanges();

        var actionsRow = new RRHBox();
        actionsRow.getStyleClass().add("git-stash-actions");

        applyButton = new RRButton("railroad.git.stash.actions.apply", FontAwesomeSolid.CHECK);
        applyButton.setVariant(ButtonVariant.PRIMARY);

        popButton = new RRButton("railroad.git.stash.actions.pop", FontAwesomeSolid.UNDO);
        popButton.setVariant(ButtonVariant.SECONDARY);

        dropButton = new RRButton("railroad.git.stash.actions.drop", FontAwesomeSolid.TRASH);
        dropButton.setVariant(ButtonVariant.DANGER);

        actionsRow.getChildren().addAll(applyButton, popButton, dropButton);

        getChildren().addAll(controlsRow, stashesList, stashChangesTree, actionsRow);

        stashesList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            updateActionState();
            onStashSelectionChanged(newValue);
        });
        stashChangesTree.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null || !(newValue.getValue() instanceof FileItem fileItem) || selectedStashRef == null)
                return;

            openDiffForStashFile(selectedStashRef, fileItem.change());
        });
        createButton.setOnAction($ -> onCreateStash());
        refreshButton.setOnAction($ -> refreshStashes());
        applyButton.setOnAction($ -> onApplyStash());
        popButton.setOnAction($ -> onPopStash());
        dropButton.setOnAction($ -> onDropStash());
        gitManager.repoStatusProperty().addListener((obs, oldValue, newValue) -> refreshStashes());

        elapsedTimeline.setCycleCount(Timeline.INDEFINITE);
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                elapsedTimeline.stop();
            } else {
                elapsedTick.set(0);
                elapsedTimeline.play();
            }
        });

        updateActionState();
        refreshStashes();
    }

    private void onCreateStash() {
        String message = messageField.getText() == null ? "" : messageField.getText().trim();
        if (message.isBlank()) {
            message = "Railroad: stash changes";
        }

        gitManager.stashChanges(message, includeUntrackedCheckBox.isSelected());
    }

    private void onApplyStash() {
        GitStashEntry selected = stashesList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            gitManager.stashApply(selected.reference());
        }
    }

    private void onPopStash() {
        GitStashEntry selected = stashesList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            gitManager.stashPop(selected.reference());
        }
    }

    private void onDropStash() {
        GitStashEntry selected = stashesList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            DialogBuilder dialogBuilder = DialogBuilder.create()
                .title("railroad.git.stash.drop_dialog.subtitle")
                .content("railroad.git.stash.drop_dialog.content")
                .onConfirm(() -> gitManager.stashDrop(selected.reference()));
            WindowBuilder.createDialog(
                "railroad.git.stash.drop_dialog.title",
                dialogBuilder
            );
        }
    }

    private void refreshStashes() {
        refreshButton.setDisable(true);
        CompletableFuture
            .supplyAsync(gitManager::getStashes)
            .exceptionally($ -> List.of())
            .thenAccept(stashes -> Platform.runLater(() -> {
                stashesList.getItems().setAll(stashes);
                updateActionState();
                refreshButton.setDisable(false);

                GitStashEntry selected = stashesList.getSelectionModel().getSelectedItem();
                if (selected == null || stashes.stream().noneMatch(stash -> stash.reference().equals(selected.reference()))) {
                    selectedStashRef = null;
                    clearStashChanges();
                }
            }));
    }

    private void updateActionState() {
        boolean hasSelection = stashesList.getSelectionModel().getSelectedItem() != null;
        applyButton.setDisable(!hasSelection);
        popButton.setDisable(!hasSelection);
        dropButton.setDisable(!hasSelection);
    }

    private void onStashSelectionChanged(GitStashEntry selectedStash) {
        selectedStashRef = selectedStash == null ? null : selectedStash.reference();
        if (selectedStashRef == null) {
            clearStashChanges();
            return;
        }

        loadStashChanges(selectedStashRef);
    }

    private void loadStashChanges(String stashRef) {
        CompletableFuture
            .supplyAsync(() -> gitManager.getStashChanges(stashRef))
            .exceptionally($ -> List.of())
            .thenAccept(changes -> Platform.runLater(() -> {
                if (!Objects.equals(selectedStashRef, stashRef))
                    return;

                setStashChanges(changes);
            }));
    }

    private void setStashChanges(List<GitFileChange> changes) {
        GitRepository repository = gitManager.getGitRepository();
        if (repository == null || changes == null || changes.isEmpty()) {
            clearStashChanges();
            return;
        }

        var root = new CommitTreeItem(RootItem.INSTANCE);
        var changesRoot = new CommitTreeItem(ChangesRootItem.INSTANCE);
        root.getChildren().add(changesRoot);

        Map<Path, CommitTreeItem> directories = new TreeMap<>(Comparator.comparing(Path::toString));
        Map<Path, List<GitFileChange>> directoryChanges = new HashMap<>();

        for (GitFileChange change : changes) {
            Path relativePath = repository.root().relativize(change.path());
            Path parent = relativePath.getParent();
            if (parent == null) {
                changesRoot.getChildren().add(new CommitTreeItem(new FileItem(project, change)));
                continue;
            }

            CommitTreeItem parentItem = null;
            Path current = Path.of("");
            for (Path part : parent) {
                current = current.resolve(part);
                List<GitFileChange> changesForDir = directoryChanges.computeIfAbsent(current, ignored -> new ArrayList<>());
                changesForDir.add(change);

                CommitTreeItem directoryItem = directories.get(current);
                if (directoryItem == null) {
                    Path directoryPath = repository.root().resolve(current).normalize();
                    directoryItem = new CommitTreeItem(new DirectoryItem(project, directoryPath, changesForDir));
                    directories.put(current, directoryItem);
                    Objects.requireNonNullElse(parentItem, changesRoot).getChildren().add(directoryItem);
                }

                parentItem = directoryItem;
            }

            Objects.requireNonNullElse(parentItem, changesRoot).getChildren()
                .add(new CommitTreeItem(new FileItem(project, change)));
        }

        changesRoot.collapseSingleChildDirectories();
        changesRoot.setExpanded(true);
        stashChangesTree.setRoot(root);
    }

    private void clearStashChanges() {
        stashChangesTree.setRoot(null);
    }

    private void openDiffForStashFile(String stashRef, GitFileChange change) {
        var scene = getScene();
        if (scene == null || scene.getRoot() == null || stashRef == null || change == null)
            return;

        var root = scene.getRoot();
        var tabPane = IDESetup.findBestPaneForFiles(root).orElse(null);
        if (tabPane == null)
            return;

        Tab diffTab = tabPane.getTabs().stream()
            .filter(tab -> tab.getContent() instanceof GitDiffPane)
            .findFirst()
            .orElseGet(() -> {
                var diffPane = new GitDiffPane(project);
                Tab created = tabPane.addTab("Git Diff", diffPane);
                created.textProperty().bind(diffPane.titleProperty());
                return created;
            });

        tabPane.getSelectionModel().select(diffTab);
        var diffPane = (GitDiffPane) diffTab.getContent();
        String title = "Git Diff: " + change.path().getFileName();
        diffPane.setExternalDiff(title, "");

        CompletableFuture
            .supplyAsync(() -> gitManager.getStashDiff(stashRef, change.path()).orElse(""))
            .thenAccept(diffText -> Platform.runLater(() -> {
                if (!Objects.equals(selectedStashRef, stashRef))
                    return;

                diffPane.setExternalDiff(title, diffText);
            }));
    }

    private static class GitStashEntryCell extends ListCell<GitStashEntry> {
        private final Text nameText = new Text();
        private final Text branchText = new Text();
        private final Text hashText = new Text();
        private final Text timestampText = new Text();
        private final Text referenceText = new Text();
        private final Text additionsText = new Text();
        private final Text deletionsText = new Text();
        private final Tooltip hashTooltip = new Tooltip();
        private final Tooltip timestampTooltip = new Tooltip();
        private final HBox itemRoot;
        private final InvalidationListener elapsedTickListener = $ -> refreshTimestampText();
        private long timestampMillis;

        private GitStashEntryCell(ReadOnlyLongProperty elapsedTick) {
            nameText.getStyleClass().add("git-stash-item-name");
            branchText.getStyleClass().add("git-stash-item-branch");
            hashText.getStyleClass().add("git-stash-item-hash");
            timestampText.getStyleClass().add("git-stash-item-timestamp");
            referenceText.getStyleClass().add("git-stash-item-reference");
            additionsText.getStyleClass().add("git-stash-item-additions");
            deletionsText.getStyleClass().add("git-stash-item-deletions");
            Tooltip.install(hashText, hashTooltip);
            Tooltip.install(timestampText, timestampTooltip);

            var metadataColumn = new VBox(branchText, hashText, timestampText);
            metadataColumn.getStyleClass().add("git-stash-item-metadata-column");
            metadataColumn.setAlignment(Pos.CENTER_LEFT);

            var leftContent = new VBox(nameText, metadataColumn);
            leftContent.getStyleClass().add("git-stash-item-left");
            HBox.setHgrow(leftContent, Priority.ALWAYS);

            var stats = new HBox(additionsText, deletionsText);
            stats.getStyleClass().add("git-stash-item-stats");
            stats.setAlignment(Pos.CENTER_RIGHT);

            var rightContent = new HBox(referenceText, stats);
            rightContent.getStyleClass().add("git-stash-item-right");
            rightContent.setAlignment(Pos.CENTER_RIGHT);

            itemRoot = new HBox(leftContent, rightContent);
            itemRoot.getStyleClass().add("git-stash-item");
            itemRoot.setAlignment(Pos.CENTER_LEFT);
            itemRoot.setFillHeight(true);

            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            setGraphic(itemRoot);
            itemRoot.prefWidthProperty().bind(widthProperty().subtract(12));

            elapsedTick.addListener(new WeakInvalidationListener(elapsedTickListener));
        }

        @Override
        protected void updateItem(GitStashEntry stashEntry, boolean empty) {
            super.updateItem(stashEntry, empty);
            if (empty || stashEntry == null) {
                setGraphic(null);
                setText(null);
                hashTooltip.setText(null);
                timestampTooltip.setText(null);
                timestampMillis = -1L;
                return;
            }

            nameText.setText(stashEntry.message());
            branchText.setText(stashEntry.branch().isBlank() ? "(no branch)" : stashEntry.branch());
            hashText.setText(stashEntry.commitHash().length() >= 7
                ? stashEntry.commitHash().substring(0, 7)
                : stashEntry.commitHash());
            hashTooltip.setText(stashEntry.commitHash());
            timestampMillis = stashEntry.createdAtEpochSeconds() * 1000L;
            refreshTimestampText();
            referenceText.setText(stashEntry.reference());
            additionsText.setText("+" + stashEntry.additions());
            deletionsText.setText("-" + stashEntry.deletions());

            setGraphic(itemRoot);
            setText(null);
        }

        private void refreshTimestampText() {
            if (timestampMillis < 0) {
                timestampText.setText(null);
                timestampTooltip.setText(null);
                return;
            }

            timestampText.setText(TimeFormatter.formatElapsed(timestampMillis));
            timestampTooltip.setText(TimeFormatter.formatDateTime(timestampMillis));
        }
    }
}
