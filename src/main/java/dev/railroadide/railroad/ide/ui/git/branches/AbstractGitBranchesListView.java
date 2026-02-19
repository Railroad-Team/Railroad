package dev.railroadide.railroad.ide.ui.git.branches;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.*;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.ui.localized.LocalizedText;
import dev.railroadide.railroad.ui.localized.LocalizedTooltip;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.utility.TimeFormatter;
import dev.railroadide.railroad.vcs.git.GitManager;
import dev.railroadide.railroad.vcs.git.branch.GitBranch;
import dev.railroadide.railroad.vcs.git.branch.GitBranchLastCommit;
import dev.railroadide.railroad.vcs.git.branch.GitBranchStatus;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import dev.railroadide.railroad.vcs.git.identity.GitAuthor;
import dev.railroadide.railroad.vcs.git.status.GitFileChange;
import dev.railroadide.railroad.vcs.git.status.GitRepoStatus;
import dev.railroadide.railroad.window.DialogBuilder;
import dev.railroadide.railroad.window.WindowBuilder;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Callback;
import javafx.util.Duration;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

abstract class AbstractGitBranchesListView<T extends GitBranch> extends RRListView<T> {
    private final Project project;
    private final ObservableList<T> branches = FXCollections.observableArrayList();
    private final StringProperty filterText = new SimpleStringProperty("");
    private Popup activeDetailsPopup;

    protected AbstractGitBranchesListView(
        Project project,
        String styleClass,
        Function<Project, List<T>> branchProvider,
        Callback<ListView<T>, ListCell<T>> cellFactory
    ) {
        this.project = project;

        Callback<ListView<T>, ListCell<T>> newCellFactory = cellFactory == null ? null : listView -> {
            ListCell<T> cell = cellFactory.call(listView);
            cell.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !cell.isEmpty()) {
                    T branch = cell.getItem();
                    onBranchDoubleClicked(branch, cell);
                }
            });
            return cell;
        };

        getStyleClass().add(styleClass);
        setCellFactory(newCellFactory);
        updateBranches(project, branchProvider);

        filterText.addListener((observable, oldValue, newValue) -> {
            String lowerCaseFilter = newValue == null ? "" : newValue.toLowerCase(Locale.ROOT);
            setItems(new FilteredList<>(branches, branch -> branch.name().toLowerCase(Locale.ROOT).contains(lowerCaseFilter)));
        });
    }

    private void onBranchDoubleClicked(T branch, ListCell<T> cell) {
        Platform.runLater(() -> {
            Node detailsNode = createDetailsNode(branch);
            showBranchDetails(detailsNode, cell);
        });
    }

    private void showBranchDetails(Node detailsNode, Node anchorNode) {
        if (detailsNode == null)
            return;

        if (activeDetailsPopup != null && activeDetailsPopup.isShowing()) {
            activeDetailsPopup.hide();
        }

        var popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);
        popup.setHideOnEscape(true);

        var container = new RRStackPane(detailsNode);
        container.getStyleClass().add("git-branch-details-popup");
        popup.getContent().add(container);

        Bounds anchorBounds = anchorNode.localToScreen(anchorNode.getBoundsInLocal());
        if (anchorBounds == null) {
            Bounds listBounds = localToScreen(getBoundsInLocal());
            if (listBounds == null)
                return;

            anchorBounds = listBounds;
        }

        Window owner = getScene() == null ? null : getScene().getWindow();
        double x = anchorBounds.getMinX();
        double y = anchorBounds.getMaxY() + 4;
        if (owner != null) {
            popup.show(owner, x, y);
        } else {
            popup.show(anchorNode, x, y);
        }

        popup.setOnHidden(event -> {
            if (activeDetailsPopup == popup) {
                activeDetailsPopup = null;
            }
        });
        activeDetailsPopup = popup;
    }

    abstract Node createDetailsNode(T branch);

    protected final Node createCommonDetailsNode(T branch, List<Node> extraRows) {
        var root = new RRVBox(8);
        root.getStyleClass().add("git-branch-details-node");

        var header = new RRHBox(8);
        header.getStyleClass().add("git-branch-details-header");
        header.setAlignment(Pos.CENTER_LEFT);

        var name = new Label(branch.name());
        name.getStyleClass().add("git-branch-details-name");

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        var status = new LocalizedLabel("");
        status.getStyleClass().add("git-branch-details-status");
        setStatus(status, branch.status());

        header.getChildren().addAll(name, spacer, status);

        var actionRow = createActionButtonsRow(branch);

        var body = new RRVBox(6);
        body.getStyleClass().add("git-branch-details-body");

        List<Node> rows = new ArrayList<>(extraRows);
        appendCommitRows(rows, branch.lastCommit());
        body.getChildren().addAll(rows);

        root.getChildren().addAll(header, actionRow, body);
        return root;
    }

    private HBox createActionButtonsRow(T branch) {
        var row = new RRHBox(6);
        row.getStyleClass().add("git-branch-details-actions");
        row.setAlignment(Pos.CENTER_LEFT);

        var checkoutButton = createActionButton("railroad.git.branches.actions.checkout", FontAwesomeSolid.CHECK);
        var setUpstreamButton = createActionButton("railroad.git.branches.actions.set_upstream", FontAwesomeSolid.CODE_BRANCH);
        var unsetUpstreamButton = createActionButton("railroad.git.branches.actions.unset_upstream", FontAwesomeSolid.TIMES);
        var renameButton = createActionButton("railroad.git.branches.actions.rename", FontAwesomeSolid.PENCIL_ALT);
        var deleteButton = createActionButton("railroad.git.branches.actions.delete", FontAwesomeSolid.TRASH);

        unsetUpstreamButton.setVariant(ButtonVariant.DANGER);
        deleteButton.setVariant(ButtonVariant.DANGER);
        deleteButton.getStyleClass().add("git-branch-details-action-delete");

        boolean showSetUpstream = false;
        boolean showUnsetUpstream = false;
        GitBranch.LocalGitBranch localBranch = null;
        if (branch instanceof GitBranch.LocalGitBranch candidateLocalBranch) {
            localBranch = candidateLocalBranch;
            boolean hasUpstream = candidateLocalBranch.remoteName() != null && !candidateLocalBranch.remoteName().isBlank();
            showSetUpstream = !hasUpstream;
            showUnsetUpstream = hasUpstream;

            if (candidateLocalBranch.isCurrent()) {
                checkoutButton.setDisable(true);
                deleteButton.setDisable(true);
            }
        }

        checkoutButton.setOnAction(event -> checkoutBranch(branch));
        if (localBranch != null) {
            GitBranch.LocalGitBranch finalLocalBranch = localBranch;
            setUpstreamButton.setOnAction(event -> openSetUpstreamDialog(finalLocalBranch));
            unsetUpstreamButton.setOnAction(event -> openUnsetUpstreamDialog(finalLocalBranch));
            renameButton.setOnAction(event -> openRenameBranchDialog(finalLocalBranch));
            deleteButton.setOnAction(event -> openDeleteBranchDialog(finalLocalBranch));
        }

        setUpstreamButton.setVisible(showSetUpstream);
        setUpstreamButton.setManaged(showSetUpstream);
        unsetUpstreamButton.setVisible(showUnsetUpstream);
        unsetUpstreamButton.setManaged(showUnsetUpstream);

        row.getChildren().addAll(
            checkoutButton,
            setUpstreamButton,
            unsetUpstreamButton,
            renameButton,
            deleteButton
        );

        for (Node actionButton : row.getChildren()) {
            if (actionButton instanceof Region region) {
                HBox.setHgrow(region, Priority.ALWAYS);
                region.setMaxWidth(Double.MAX_VALUE);
            }
        }

        return row;
    }

    private void openSetUpstreamDialog(GitBranch.LocalGitBranch localBranch) {
        List<String> remoteBranches = project.getGitManager().getAllRemoteBranchNames()
            .stream()
            .filter(Objects::nonNull)
            .filter(branch -> !branch.endsWith("/HEAD"))
            .distinct()
            .sorted()
            .collect(Collectors.toList());

        var content = new RRVBox(10);
        content.getStyleClass().add("git-branch-set-upstream-dialog-content");

        var localBranchLabel = new LocalizedText("railroad.git.branches.set_upstream_dialog.local_branch", localBranch.name());
        localBranchLabel.getStyleClass().add("git-branch-set-upstream-local-branch-label");
        content.getChildren().add(localBranchLabel);

        var upstreamLabel = new LocalizedText("railroad.git.branches.set_upstream_dialog.upstream_branch");
        upstreamLabel.getStyleClass().add("git-branch-set-upstream-upstream-label");
        content.getChildren().add(upstreamLabel);

        var upstreamComboBox = new ComboBox<String>();
        upstreamComboBox.getStyleClass().add("git-branch-set-upstream-combo-box");
        upstreamComboBox.setEditable(true);
        upstreamComboBox.setPromptText("origin/" + localBranch.name());
        upstreamComboBox.getItems().setAll(remoteBranches);
        String defaultUpstream = "origin/" + localBranch.name();
        if (remoteBranches.contains(defaultUpstream)) {
            upstreamComboBox.getEditor().setText(defaultUpstream);
        }
        content.getChildren().add(upstreamComboBox);

        var errorText = new LocalizedText("");
        errorText.getStyleClass().add("git-branch-set-upstream-error-text");
        content.getChildren().add(errorText);

        DialogBuilder dialogBuilder = DialogBuilder.create()
            .title("railroad.git.branches.set_upstream_dialog.title")
            .contentNode(content)
            .onConfirm(() -> {
                String selectedUpstream = Objects.requireNonNullElse(upstreamComboBox.getEditor().getText(), "").trim();
                if (remoteBranches.contains(selectedUpstream)) {
                    project.getGitManager().setBranchUpstream(localBranch.name(), selectedUpstream);
                }
            });

        var dialog = WindowBuilder.createDialog("railroad.git.branches.set_upstream_dialog.title", dialogBuilder);
        var confirmButton = (RRButton) dialog.getScene().lookup(".rr-button.success");
        if (confirmButton != null) {
            validateUpstreamSelection(upstreamComboBox.getEditor().getText(), remoteBranches, errorText, confirmButton);
            upstreamComboBox.getEditor().textProperty().addListener((obs, oldText, newText) ->
                validateUpstreamSelection(newText, remoteBranches, errorText, confirmButton)
            );
            upstreamComboBox.valueProperty().addListener((obs, oldValue, newValue) ->
                validateUpstreamSelection(newValue, remoteBranches, errorText, confirmButton)
            );
        }
    }

    private static void validateUpstreamSelection(
        @Nullable String selectedUpstream,
        List<String> remoteBranches,
        LocalizedText errorText,
        RRButton confirmButton
    ) {
        String value = Objects.requireNonNullElse(selectedUpstream, "").trim();
        if (value.isBlank()) {
            errorText.setKeyAndArgs("");
            confirmButton.setDisable(true);
            return;
        }

        if (!remoteBranches.contains(value)) {
            errorText.setKeyAndArgs("railroad.git.branches.set_upstream_dialog.error_remote_branch_not_found");
            confirmButton.setDisable(true);
            return;
        }

        errorText.setKeyAndArgs("");
        confirmButton.setDisable(false);
    }

    private void openUnsetUpstreamDialog(GitBranch.LocalGitBranch localBranch) {
        String upstream = localBranch.remoteName();
        if (upstream == null || upstream.isBlank()) {
            return;
        }

        var content = new LocalizedText(
            "railroad.git.branches.unset_upstream_dialog.content",
            localBranch.name(),
            upstream
        );
        content.getStyleClass().add("git-branch-unset-upstream-dialog-content");

        var cancelButton = new RRButton("railroad.generic.cancel");
        cancelButton.setVariant(ButtonVariant.SECONDARY);
        cancelButton.getStyleClass().add("git-branch-unset-upstream-dialog-cancel-button");

        var confirmButton = new RRButton("railroad.git.branches.unset_upstream_dialog.confirm");
        confirmButton.setVariant(ButtonVariant.DANGER);
        confirmButton.getStyleClass().add("git-branch-unset-upstream-dialog-confirm-button");

        DialogBuilder dialogBuilder = DialogBuilder.create()
            .title("railroad.git.branches.unset_upstream_dialog.subtitle")
            .contentNode(content)
            .buttons(cancelButton, confirmButton);
        Stage dialog = WindowBuilder.createDialog("railroad.git.branches.unset_upstream_dialog.title", dialogBuilder);

        cancelButton.setOnAction($ -> dialog.close());
        confirmButton.setOnAction($ -> {
            project.getGitManager().unsetBranchUpstream(localBranch.name());
            dialog.close();
        });
    }

    private void openRenameBranchDialog(GitBranch.LocalGitBranch localBranch) {
        var content = new RRVBox(10);
        content.getStyleClass().add("git-branch-rename-dialog-content");

        var currentBranchLabel = new LocalizedText("railroad.git.branches.rename_dialog.current_branch", localBranch.name());
        currentBranchLabel.getStyleClass().add("git-branch-rename-current-branch-label");
        content.getChildren().add(currentBranchLabel);

        var newNameLabel = new LocalizedText("railroad.git.branches.rename_dialog.new_name_label");
        newNameLabel.getStyleClass().add("git-branch-rename-new-name-label");
        content.getChildren().add(newNameLabel);

        var newNameField = new RRTextField("railroad.git.branches.rename_dialog.new_name_placeholder");
        newNameField.getStyleClass().add("git-branch-rename-new-name-field");
        newNameField.setText(localBranch.name());
        content.getChildren().add(newNameField);

        var errorText = new LocalizedText("");
        errorText.getStyleClass().add("git-branch-rename-error-text");
        content.getChildren().add(errorText);

        var forceCheckbox = new RRCheckBox("railroad.git.branches.rename_dialog.force_checkbox");
        forceCheckbox.getStyleClass().add("git-branch-rename-force-checkbox");
        forceCheckbox.setSelected(false);
        content.getChildren().add(forceCheckbox);

        DialogBuilder dialogBuilder = DialogBuilder.create()
            .title("railroad.git.branches.rename_dialog.title")
            .contentNode(content)
            .onConfirm(() -> {
                String newName = newNameField.getText().trim();
                if (isValidRenameBranchName(newName, localBranch.name())) {
                    project.getGitManager().renameBranch(localBranch.name(), newName, forceCheckbox.isSelected());
                }
            });

        var dialog = WindowBuilder.createDialog("railroad.git.branches.rename_dialog.title", dialogBuilder);
        var confirmButton = (RRButton) dialog.getScene().lookup(".rr-button.success");
        if (confirmButton != null) {
            validateRenameBranchName(localBranch.name(), newNameField.getText(), errorText, confirmButton);
            newNameField.textProperty().addListener((obs, oldText, newText) ->
                validateRenameBranchName(localBranch.name(), newText, errorText, confirmButton)
            );
        }
    }

    private boolean isValidRenameBranchName(@Nullable String proposedName, String currentName) {
        String value = Objects.requireNonNullElse(proposedName, "").trim();
        return !value.isBlank() && !value.equals(currentName) && project.getGitManager().isValidBranchName(value);
    }

    private void validateRenameBranchName(
        String currentName,
        @Nullable String proposedName,
        LocalizedText errorText,
        RRButton confirmButton
    ) {
        String value = Objects.requireNonNullElse(proposedName, "").trim();
        if (value.isBlank()) {
            errorText.setKeyAndArgs("");
            confirmButton.setDisable(true);
            return;
        }

        if (value.equals(currentName)) {
            errorText.setKeyAndArgs("railroad.git.branches.rename_dialog.error_same_name");
            confirmButton.setDisable(true);
            return;
        }

        boolean hasControlChars = value.chars().anyMatch(c -> c < 32 || c == 127);
        if (hasControlChars) {
            errorText.setKeyAndArgs("railroad.git.commit.details.new_branch_dialog.error_invalid_characters");
            confirmButton.setDisable(true);
            return;
        }

        if (value.contains(" ")) {
            errorText.setKeyAndArgs("railroad.git.commit.details.new_branch_dialog.error_no_spaces");
            confirmButton.setDisable(true);
            return;
        }

        if (value.endsWith(".") || value.endsWith("/")) {
            errorText.setKeyAndArgs("railroad.git.commit.details.new_branch_dialog.error_invalid_ending");
            confirmButton.setDisable(true);
            return;
        }

        if (value.contains("..")) {
            errorText.setKeyAndArgs("railroad.git.commit.details.new_branch_dialog.error_double_dots");
            confirmButton.setDisable(true);
            return;
        }

        if (value.contains("//") || value.contains("@{") || value.contains("\\")) {
            errorText.setKeyAndArgs("railroad.git.commit.details.new_branch_dialog.error_invalid_sequences");
            confirmButton.setDisable(true);
            return;
        }

        if (value.equals("HEAD")) {
            errorText.setKeyAndArgs("railroad.git.commit.details.new_branch_dialog.error_head_reserved");
            confirmButton.setDisable(true);
            return;
        }

        if (!project.getGitManager().isValidBranchName(value)) {
            errorText.setKeyAndArgs("railroad.git.commit.details.new_branch_dialog.error_invalid_branch_name");
            confirmButton.setDisable(true);
            return;
        }

        errorText.setKeyAndArgs("");
        confirmButton.setDisable(false);
    }

    private void openDeleteBranchDialog(GitBranch.LocalGitBranch localBranch) {
        if (localBranch.isCurrent()) {
            return;
        }

        var content = new RRVBox(10);
        content.getStyleClass().add("git-branch-delete-dialog-content");

        var infoText = new LocalizedText(
            "railroad.git.branches.delete_dialog.content",
            localBranch.name()
        );
        infoText.getStyleClass().add("git-branch-delete-dialog-info-text");
        content.getChildren().add(infoText);

        var confirmField = new RRTextField("railroad.git.branches.delete_dialog.confirmation_placeholder");
        confirmField.getStyleClass().add("git-branch-delete-dialog-confirmation-field");
        content.getChildren().add(confirmField);

        var errorText = new LocalizedText("");
        errorText.getStyleClass().add("git-branch-delete-dialog-error-text");
        content.getChildren().add(errorText);

        var cancelButton = new RRButton("railroad.generic.cancel");
        cancelButton.setVariant(ButtonVariant.SECONDARY);
        cancelButton.getStyleClass().add("git-branch-delete-dialog-cancel-button");

        var confirmButton = new RRButton("railroad.git.branches.delete_dialog.confirm");
        confirmButton.setVariant(ButtonVariant.DANGER);
        confirmButton.getStyleClass().add("git-branch-delete-dialog-confirm-button");
        confirmButton.setDisable(true);

        DialogBuilder dialogBuilder = DialogBuilder.create()
            .title("railroad.git.branches.delete_dialog.subtitle")
            .contentNode(content)
            .buttons(cancelButton, confirmButton);
        Stage dialog = WindowBuilder.createDialog("railroad.git.branches.delete_dialog.title", dialogBuilder);

        cancelButton.setOnAction($ -> dialog.close());
        confirmButton.setOnAction($ -> {
            if ("CONFIRM".equals(confirmField.getText())) {
                project.getGitManager().deleteBranch(localBranch.name(), false);
                dialog.close();
            }
        });

        confirmField.textProperty().addListener((obs, oldText, newText) -> {
            boolean matches = "CONFIRM".equals(newText);
            confirmButton.setDisable(!matches);
            if (newText == null || newText.isBlank() || matches) {
                errorText.setKeyAndArgs("");
            } else {
                errorText.setKeyAndArgs("railroad.git.branches.delete_dialog.error_confirmation_mismatch");
            }
        });
    }

    private void checkoutBranch(T branch) {
        GitManager gitManager = project.getGitManager();
        GitRepoStatus repoStatus = gitManager.getRepoStatus();
        if (repoStatus != null && !repoStatus.changes().isEmpty()) {
            onCheckoutWithUncommittedChanges(gitManager, gitManager.getCurrentCommit(), branch.name(), repoStatus);
            return;
        }

        gitManager.checkoutBranch(branch.name());
    }

    private static void onCheckoutWithUncommittedChanges(
        GitManager gitManager,
        Optional<GitCommit> fromCommit,
        String targetBranchName,
        GitRepoStatus repoStatus
    ) {
        var content = new RRVBox(2);
        content.getStyleClass().add("git-commit-checkout-uncommitted-changes-dialog-content");

        var infoText = new LocalizedText("railroad.git.commit.details.checkout_commit_dialog.uncommitted_changes_info");
        infoText.getStyleClass().add("git-commit-checkout-uncommitted-changes-info-text");
        content.getChildren().add(infoText);

        var unstagedChangesText = new LocalizedText(
            "railroad.git.commit.details.checkout_commit_dialog.unstaged_changes",
            repoStatus.changes().stream().filter(GitFileChange::isUnstaged).count()
        );
        unstagedChangesText.getStyleClass().add("git-commit-checkout-unstaged-changes-text");
        content.getChildren().add(unstagedChangesText);

        var stagedChangesText = new LocalizedText(
            "railroad.git.commit.details.checkout_commit_dialog.staged_changes",
            repoStatus.changes().stream().filter(GitFileChange::isStaged).count()
        );
        stagedChangesText.getStyleClass().add("git-commit-checkout-staged-changes-text");
        content.getChildren().add(stagedChangesText);

        var untrackedChangesText = new LocalizedText(
            "railroad.git.commit.details.checkout_commit_dialog.untracked_changes",
            repoStatus.changes().stream().filter(GitFileChange::isUntracked).count()
        );
        untrackedChangesText.getStyleClass().add("git-commit-checkout-untracked-changes-text");
        content.getChildren().add(untrackedChangesText);

        var cancelButton = new RRButton("railroad.generic.cancel");
        cancelButton.setVariant(ButtonVariant.SECONDARY);
        cancelButton.getStyleClass().add("git-commit-checkout-uncommitted-changes-cancel-button");

        var stashAndCheckoutButton = new RRButton("railroad.git.commit.details.checkout_commit_dialog.stash_and_checkout");
        stashAndCheckoutButton.setVariant(ButtonVariant.PRIMARY);
        stashAndCheckoutButton.getStyleClass().add("git-commit-checkout-uncommitted-changes-stash-and-checkout-button");

        var forceCheckoutButton = new RRButton("railroad.git.commit.details.checkout_commit_dialog.force_checkout");
        forceCheckoutButton.setVariant(ButtonVariant.DANGER);
        forceCheckoutButton.getStyleClass().add("git-commit-checkout-uncommitted-changes-force-checkout-button");

        DialogBuilder dialogBuilder = DialogBuilder.create()
            .title("railroad.git.commit.details.checkout_commit_dialog.subtitle")
            .contentNode(content)
            .buttons(cancelButton, stashAndCheckoutButton, forceCheckoutButton);
        Stage dialog = WindowBuilder.createDialog("railroad.git.commit.details.checkout_commit_dialog.title", dialogBuilder);

        cancelButton.setOnAction($ -> dialog.close());

        stashAndCheckoutButton.setOnAction($ -> {
            gitManager.stashChanges("Railroad: before checkout " + fromCommit.map(GitCommit::shortHash).orElse("HEAD"), true);
            gitManager.checkoutBranch(targetBranchName);
            dialog.close();
        });

        forceCheckoutButton.setOnAction($ -> {
            var discardTextField = new RRTextField("railroad.git.commit.details.checkout_commit_dialog.force_checkout.confirmation_placeholder");
            discardTextField.getStyleClass().add("git-commit-checkout-force-checkout-confirmation-text-field");

            var forceContent = new RRVBox(10);
            forceContent.getStyleClass().add("git-commit-checkout-force-checkout-dialog-content");
            var forceInfoText = new LocalizedText("railroad.git.commit.details.checkout_commit_dialog.force_checkout_info");
            forceInfoText.getStyleClass().add("git-commit-checkout-force-checkout-info-text");
            forceContent.getChildren().add(forceInfoText);
            forceContent.getChildren().add(discardTextField);

            AtomicReference<Stage> forceDialogRef = new AtomicReference<>();
            DialogBuilder forceDialogBuilder = DialogBuilder.create()
                .title("railroad.git.commit.details.checkout_commit_dialog.force_checkout_title")
                .contentNode(forceContent)
                .onCancel(() -> {
                    Stage forceDialogStage = forceDialogRef.get();
                    if (forceDialogStage != null) {
                        forceDialogStage.close();
                    }
                })
                .onConfirm(() -> {
                    if (discardTextField.getText().equals("FORCE")) {
                        gitManager.resetHard();
                        gitManager.cleanUntrackedFiles();
                        gitManager.checkoutBranch(targetBranchName);
                        Stage forceDialogStage = forceDialogRef.get();
                        if (forceDialogStage != null) {
                            forceDialogStage.close();
                        }
                        dialog.close();
                    }
                });

            Stage forceDialog = WindowBuilder.createDialog(
                "railroad.git.commit.details.checkout_commit_dialog.force_checkout_title",
                forceDialogBuilder
            );
            forceDialogRef.set(forceDialog);

            var confirmButton = (RRButton) forceDialog.getScene().lookup(".rr-button.primary");
            if (confirmButton != null) {
                confirmButton.setDisable(true);
                discardTextField.textProperty().addListener((obs, oldText, newText) -> confirmButton.setDisable(!newText.equals("FORCE")));
            }
        });
    }

    private RRButton createActionButton(String key, FontAwesomeSolid icon) {
        var button = new RRButton(key, icon);
        button.getStyleClass().add("git-branch-details-action-button");
        button.setTooltip(new LocalizedTooltip(key));
        return button;
    }

    protected final HBox createTextDetailsRow(String labelKey, @Nullable String value) {
        return createDetailsRow(labelKey, createTextValueNode(value));
    }

    protected final HBox createLocalizedDetailsRow(String labelKey, String valueKey, Object... args) {
        var value = new LocalizedLabel("");
        value.getStyleClass().add("git-branch-details-value");
        value.setKey(valueKey, args);
        return createDetailsRow(labelKey, value);
    }

    private HBox createDetailsRow(String labelKey, Node valueNode) {
        var row = new RRHBox(8);
        row.getStyleClass().add("git-branch-details-row");
        row.setAlignment(Pos.CENTER_LEFT);

        var label = new LocalizedLabel(labelKey);
        label.getStyleClass().add("git-branch-details-label");

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(label, spacer, valueNode);
        return row;
    }

    private Node createTextValueNode(@Nullable String value) {
        if (value == null || value.isBlank()) {
            var unknown = new LocalizedLabel("railroad.generic.unknown");
            unknown.getStyleClass().add("git-branch-details-value");
            return unknown;
        }

        var label = new Label(value);
        label.getStyleClass().add("git-branch-details-value");
        return label;
    }

    private void appendCommitRows(List<Node> rows, @Nullable GitBranchLastCommit lastCommit) {
        if (lastCommit == null) {
            rows.add(createLocalizedDetailsRow("railroad.git.branches.details.last_commit_hash", "railroad.git.branches.no_commit_hash"));
            rows.add(createLocalizedDetailsRow("railroad.git.branches.details.last_commit_message", "railroad.generic.unknown"));
            rows.add(createLocalizedDetailsRow("railroad.git.branches.details.last_commit_author", "railroad.generic.unknown"));
            rows.add(createLocalizedDetailsRow("railroad.git.branches.details.last_commit_author_email", "railroad.generic.unknown"));
            rows.add(createLocalizedDetailsRow("railroad.git.branches.details.last_commit_time", "railroad.git.branches.last.never"));
            return;
        }

        String hash = lastCommit.hash();
        if (hash == null || hash.isBlank()) {
            rows.add(createLocalizedDetailsRow("railroad.git.branches.details.last_commit_hash", "railroad.git.branches.no_commit_hash"));
        } else {
            rows.add(createTextDetailsRow("railroad.git.branches.details.last_commit_hash", hash));
        }

        rows.add(createTextDetailsRow("railroad.git.branches.details.last_commit_message", lastCommit.message()));

        GitAuthor author = lastCommit.author();
        rows.add(createTextDetailsRow("railroad.git.branches.details.last_commit_author", author == null ? null : author.name()));
        rows.add(createTextDetailsRow("railroad.git.branches.details.last_commit_author_email", author == null ? null : author.email()));

        Long timestampSeconds = lastCommit.timestampEpochSeconds();
        if (timestampSeconds == null) {
            rows.add(createLocalizedDetailsRow("railroad.git.branches.details.last_commit_time", "railroad.git.branches.last.never"));
            return;
        }

        String formattedDateTime = TimeFormatter.formatDateTime(timestampSeconds * 1000L);
        rows.add(createElapsedDetailsRow(timestampSeconds * 1000L, formattedDateTime));
    }

    private HBox createElapsedDetailsRow(long timestampMillis, String formattedDateTime) {
        var value = new LocalizedLabel("");
        value.getStyleClass().add("git-branch-details-value");

        Runnable refresh = () -> value.setKey(
            "railroad.git.branches.details.last_commit_time_value",
            formattedDateTime,
            TimeFormatter.formatElapsed(timestampMillis)
        );
        refresh.run();

        var elapsedTimeline = new Timeline(new KeyFrame(Duration.seconds(1), $ -> refresh.run()));
        elapsedTimeline.setCycleCount(Timeline.INDEFINITE);
        value.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                elapsedTimeline.stop();
            } else {
                refresh.run();
                elapsedTimeline.play();
            }
        });

        return createDetailsRow("railroad.git.branches.details.last_commit_time", value);
    }

    private static void setStatus(LocalizedLabel statusLabel, @Nullable GitBranchStatus status) {
        if (status == null) {
            statusLabel.setKey("railroad.generic.unknown");
        } else {
            statusLabel.setKey(status.getTranslationKey());
        }
    }

    private void updateBranches(Project project, Function<Project, List<T>> branchProvider) {
        branches.setAll(branchProvider.apply(project));
        setItems(new FilteredList<>(branches, branch -> branch.name().toLowerCase(Locale.ROOT).contains(filterText.get().toLowerCase(Locale.ROOT))));
    }

    public void filterBranches(String newValue) {
        filterText.set(Objects.requireNonNullElse(newValue, "").toLowerCase(Locale.ROOT));
    }
}
