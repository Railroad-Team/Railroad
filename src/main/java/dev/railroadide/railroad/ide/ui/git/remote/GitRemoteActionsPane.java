package dev.railroadide.railroad.ide.ui.git.remote;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.command.CommandButtons;
import dev.railroadide.railroad.command.CommandContext;
import dev.railroadide.railroad.command.GitCommands;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRTextField;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.ui.localized.LocalizedText;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.utility.DesktopUtils;
import dev.railroadide.railroad.vcs.git.GitManager;
import dev.railroadide.railroad.vcs.git.remote.GitRemote;
import dev.railroadide.railroad.vcs.git.remote.GitUpstream;
import dev.railroadide.railroad.window.DialogBuilder;
import dev.railroadide.railroad.window.WindowBuilder;
import javafx.geometry.Pos;
import javafx.stage.Stage;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.util.List;
import java.util.Objects;

/**
 * Provides remote configuration, fetching, pruning, and browser actions.
 */
public class GitRemoteActionsPane extends RRVBox {
    private final GitManager gitManager;

    private final RRButton editRemoteButton;
    private final RRButton removeRemoteButton;
    private final RRButton fetchButton;
    private final RRButton pruneButton;
    private final RRButton openInBrowserButton;

    private GitRemote selectedRemote;

    /**
     * Creates remote management actions for a repository.
     *
     * @param gitManager repository service supplying state and Git operations
     */
    public GitRemoteActionsPane(GitManager gitManager) {
        Services.UI_MANAGER.assignWhileAttached(UIIds.Git.GIT_REMOTE_ACTIONS, this);
        this.gitManager = gitManager;

        getStyleClass().add("git-remote-actions-pane");

        var fetchAllButton = new RRButton("railroad.git.remotes.actions.button.fetch_all", FontAwesomeSolid.DOWNLOAD);
        var pruneAllButton = new RRButton("railroad.git.remotes.actions.button.prune_all", FontAwesomeSolid.BROOM);
        var addRemoteButton = new RRButton("railroad.git.remotes.actions.button.add_remote", FontAwesomeSolid.PLUS);
        addRemoteButton.setVariant(ButtonVariant.SUCCESS);

        editRemoteButton = new RRButton("railroad.git.remotes.actions.button.edit_remote", FontAwesomeSolid.PEN);
        removeRemoteButton = new RRButton("railroad.git.remotes.actions.button.remove_remote", FontAwesomeSolid.TRASH);
        removeRemoteButton.setVariant(ButtonVariant.DANGER);
        fetchButton = new RRButton("railroad.git.remotes.actions.button.fetch", FontAwesomeSolid.DOWNLOAD);
        pruneButton = new RRButton("railroad.git.remotes.actions.button.prune", FontAwesomeSolid.BROOM);
        openInBrowserButton = new RRButton("railroad.git.remotes.actions.button.open_in_browser",
            FontAwesomeSolid.GLOBE);

        var primaryActionsBox = new RRHBox(fetchAllButton, pruneAllButton, addRemoteButton);
        primaryActionsBox.getStyleClass().add("git-remotes-actions-primary-actions");
        primaryActionsBox.setAlignment(Pos.CENTER);

        var secondaryActionsBox = new RRHBox(editRemoteButton, removeRemoteButton, fetchButton, pruneButton,
            openInBrowserButton);
        secondaryActionsBox.getStyleClass().add("git-remotes-actions-secondary-actions");
        secondaryActionsBox.setAlignment(Pos.CENTER);

        getChildren().addAll(primaryActionsBox, secondaryActionsBox);
        setAlignment(Pos.TOP_CENTER);

        CommandButtons.bind(fetchAllButton, GitCommands.FETCH_ALL,
            () -> CommandContext.withArgument(null, this, gitManager));
        CommandButtons.bind(pruneAllButton, GitCommands.PRUNE_ALL,
            () -> CommandContext.withArgument(null, this, gitManager));
        CommandButtons.bind(addRemoteButton, GitCommands.REMOTE_ADD,
            () -> CommandContext.withArgument(null, this, this));

        CommandButtons.bind(editRemoteButton, GitCommands.REMOTE_EDIT,
            () -> CommandContext.withArgument(null, this, this));

        CommandButtons.bind(removeRemoteButton, GitCommands.REMOTE_REMOVE,
            () -> CommandContext.withArgument(null, this, this));

        CommandButtons.bind(fetchButton, GitCommands.FETCH,
            () -> CommandContext.withArgument(null, this, gitManager));
        CommandButtons.bind(pruneButton, GitCommands.GC,
            () -> CommandContext.withArgument(null, this, gitManager));
        CommandButtons.bind(openInBrowserButton, GitCommands.REMOTE_OPEN_BROWSER,
            () -> CommandContext.withArgument(null, this, this));

        updateActions(resolveDefaultRemote());
    }

    /**
     * Selects the action target and enables remote-specific buttons only when a remote is present.
     *
     * @param remote selected remote, or null to disable remote-specific actions
     */
    public void updateActions(GitRemote remote) {
        selectedRemote = remote;
        boolean hasRemote = remote != null;

        editRemoteButton.setDisable(!hasRemote);
        removeRemoteButton.setDisable(!hasRemote);
        fetchButton.setDisable(!hasRemote);
        pruneButton.setDisable(!hasRemote);
        openInBrowserButton.setDisable(!hasRemote);
    }

    /**
     * Opens the existing remote creation dialog.
     */
    public void openAddRemoteDialog() {
        var content = new RRVBox();
        content.getStyleClass().add("git-remote-add-dialog-content");

        var nameLabel = new LocalizedText("railroad.git.remotes.actions.add_dialog.name.label");
        var nameField = new RRTextField("railroad.git.remotes.actions.add_dialog.name.placeholder");

        var fetchUrlLabel = new LocalizedText("railroad.git.remotes.actions.add_dialog.fetch_url.label");
        var fetchUrlField = new RRTextField("railroad.git.remotes.actions.add_dialog.fetch_url.placeholder");

        var pushUrlLabel = new LocalizedText("railroad.git.remotes.actions.add_dialog.push_url.label");
        var pushUrlField = new RRTextField("railroad.git.remotes.actions.add_dialog.push_url.placeholder");

        var errorText = new LocalizedText("");
        errorText.getStyleClass().add("git-remote-actions-dialog-error-text");

        content.getChildren().addAll(nameLabel, nameField, fetchUrlLabel, fetchUrlField, pushUrlLabel, pushUrlField,
            errorText);

        DialogBuilder dialogBuilder = DialogBuilder.create()
            .title("railroad.git.remotes.actions.add_dialog.subtitle")
            .contentNode(content)
            .onConfirm(() -> {
                String name = nameField.getText().trim();
                String fetchUrl = fetchUrlField.getText().trim();
                String pushUrl = pushUrlField.getText().trim();
                if (pushUrl.isBlank()) {
                    pushUrl = fetchUrl;
                }

                if (isAddRemoteInputValid(name, fetchUrl, pushUrl)) {
                    gitManager.addRemote(name, fetchUrl, pushUrl);
                }
            });

        Stage dialog = WindowBuilder.createDialog("railroad.git.remotes.actions.add_dialog.title", dialogBuilder);
        RRButton confirmButton = (RRButton) dialog.getScene().lookup(".rr-button.success");
        if (confirmButton == null)
            return;

        Runnable validator = () -> validateAddRemoteInput(nameField, fetchUrlField, pushUrlField, errorText,
            confirmButton);
        validator.run();
        nameField.textProperty().addListener((_, _, _) -> validator.run());
        fetchUrlField.textProperty().addListener((_, _, _) -> validator.run());
        pushUrlField.textProperty().addListener((_, _, _) -> validator.run());
    }

    private void openEditRemoteDialog(GitRemote remote) {
        var content = new RRVBox();
        content.getStyleClass().add("git-remote-edit-dialog-content");

        var currentRemoteText = new LocalizedText("railroad.git.remotes.actions.edit_dialog.current_remote",
            remote.name());

        var nameLabel = new LocalizedText("railroad.git.remotes.actions.edit_dialog.name.label");
        var nameField = new RRTextField("railroad.git.remotes.actions.edit_dialog.name.placeholder");
        nameField.setText(remote.name());

        var fetchUrlLabel = new LocalizedText("railroad.git.remotes.actions.edit_dialog.fetch_url.label");
        var fetchUrlField = new RRTextField("railroad.git.remotes.actions.edit_dialog.fetch_url.placeholder");
        fetchUrlField.setText(remote.fetchUrl());

        var pushUrlLabel = new LocalizedText("railroad.git.remotes.actions.edit_dialog.push_url.label");
        var pushUrlField = new RRTextField("railroad.git.remotes.actions.edit_dialog.push_url.placeholder");
        pushUrlField.setText(remote.pushUrl());

        var errorText = new LocalizedText("");
        errorText.getStyleClass().add("git-remote-actions-dialog-error-text");

        content.getChildren().addAll(currentRemoteText, nameLabel, nameField, fetchUrlLabel, fetchUrlField,
            pushUrlLabel, pushUrlField, errorText);

        DialogBuilder dialogBuilder = DialogBuilder.create()
            .title("railroad.git.remotes.actions.edit_dialog.subtitle")
            .contentNode(content)
            .onConfirm(() -> {
                String newName = nameField.getText().trim();
                String fetchUrl = fetchUrlField.getText().trim();
                String pushUrl = pushUrlField.getText().trim();
                if (pushUrl.isBlank()) {
                    pushUrl = fetchUrl;
                }

                if (isEditRemoteInputValid(remote.name(), newName, fetchUrl, pushUrl)) {
                    gitManager.updateRemote(remote.name(), newName, fetchUrl, pushUrl);
                }
            });

        Stage dialog = WindowBuilder.createDialog("railroad.git.remotes.actions.edit_dialog.title", dialogBuilder);
        RRButton confirmButton = (RRButton) dialog.getScene().lookup(".rr-button.success");
        if (confirmButton == null)
            return;

        Runnable validator = () -> validateEditRemoteInput(remote.name(), nameField, fetchUrlField, pushUrlField,
            errorText, confirmButton);
        validator.run();
        nameField.textProperty().addListener((_, _, _) -> validator.run());
        fetchUrlField.textProperty().addListener((_, _, _) -> validator.run());
        pushUrlField.textProperty().addListener((_, _, _) -> validator.run());
    }

    private void openRemoveRemoteDialog(GitRemote remote) {
        var content = new RRVBox();
        content.getStyleClass().add("git-remote-remove-dialog-content");

        var infoText = new LocalizedText("railroad.git.remotes.actions.remove_dialog.content", remote.name());
        var confirmationField = new RRTextField("railroad.git.remotes.actions.remove_dialog.confirmation_placeholder");
        var errorText = new LocalizedText("");
        errorText.getStyleClass().add("git-remote-actions-dialog-error-text");

        content.getChildren().addAll(infoText, confirmationField, errorText);

        var cancelButton = new RRButton("railroad.generic.cancel");
        cancelButton.setVariant(ButtonVariant.SECONDARY);

        var confirmButton = new RRButton("railroad.git.remotes.actions.remove_dialog.confirm");
        confirmButton.setVariant(ButtonVariant.DANGER);
        confirmButton.setDisable(true);

        DialogBuilder dialogBuilder = DialogBuilder.create()
            .title("railroad.git.remotes.actions.remove_dialog.subtitle")
            .contentNode(content)
            .buttons(cancelButton, confirmButton);
        Stage dialog = WindowBuilder.createDialog("railroad.git.remotes.actions.remove_dialog.title", dialogBuilder);

        cancelButton.setOnAction(_ -> dialog.close());
        confirmButton.setOnAction(_ -> {
            if (remote.name().equals(confirmationField.getText())) {
                gitManager.removeRemote(remote.name());
                dialog.close();
            }
        });

        confirmationField.textProperty().addListener((_, _, newText) -> {
            boolean matches = remote.name().equals(newText);
            confirmButton.setDisable(!matches);

            if (newText == null || newText.isBlank() || matches) {
                errorText.setKeyAndArgs("");
            } else {
                errorText.setKeyAndArgs("railroad.git.remotes.actions.remove_dialog.error_confirmation_mismatch");
            }
        });
    }

    private boolean isAddRemoteInputValid(String name, String fetchUrl, String pushUrl) {
        return !name.isBlank() && !fetchUrl.isBlank() && !pushUrl.isBlank() && isRemoteNameAvailable(name, null);
    }

    private boolean isEditRemoteInputValid(String currentName, String newName, String fetchUrl, String pushUrl) {
        return !newName.isBlank() && !fetchUrl.isBlank() && !pushUrl.isBlank()
            && isRemoteNameAvailable(newName, currentName);
    }

    private void validateAddRemoteInput(
        RRTextField nameField,
        RRTextField fetchUrlField,
        RRTextField pushUrlField,
        LocalizedText errorText,
        RRButton confirmButton
    ) {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        String fetchUrl = fetchUrlField.getText() == null ? "" : fetchUrlField.getText().trim();
        String pushUrl = pushUrlField.getText() == null ? "" : pushUrlField.getText().trim();
        if (pushUrl.isBlank()) {
            pushUrl = fetchUrl;
        }

        if (name.isBlank()) {
            errorText.setKeyAndArgs("railroad.git.remotes.actions.dialog.error_name_required");
            confirmButton.setDisable(true);
            return;
        }

        if (!isRemoteNameAvailable(name, null)) {
            errorText.setKeyAndArgs("railroad.git.remotes.actions.dialog.error_name_exists");
            confirmButton.setDisable(true);
            return;
        }

        if (fetchUrl.isBlank()) {
            errorText.setKeyAndArgs("railroad.git.remotes.actions.dialog.error_fetch_url_required");
            confirmButton.setDisable(true);
            return;
        }

        if (pushUrl.isBlank()) {
            errorText.setKeyAndArgs("railroad.git.remotes.actions.dialog.error_push_url_required");
            confirmButton.setDisable(true);
            return;
        }

        errorText.setKeyAndArgs("");
        confirmButton.setDisable(false);
    }

    private void validateEditRemoteInput(
        String currentName,
        RRTextField nameField,
        RRTextField fetchUrlField,
        RRTextField pushUrlField,
        LocalizedText errorText,
        RRButton confirmButton
    ) {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        String fetchUrl = fetchUrlField.getText() == null ? "" : fetchUrlField.getText().trim();
        String pushUrl = pushUrlField.getText() == null ? "" : pushUrlField.getText().trim();
        if (pushUrl.isBlank()) {
            pushUrl = fetchUrl;
        }

        if (name.isBlank()) {
            errorText.setKeyAndArgs("railroad.git.remotes.actions.dialog.error_name_required");
            confirmButton.setDisable(true);
            return;
        }

        if (!isRemoteNameAvailable(name, currentName)) {
            errorText.setKeyAndArgs("railroad.git.remotes.actions.dialog.error_name_exists");
            confirmButton.setDisable(true);
            return;
        }

        if (fetchUrl.isBlank()) {
            errorText.setKeyAndArgs("railroad.git.remotes.actions.dialog.error_fetch_url_required");
            confirmButton.setDisable(true);
            return;
        }

        if (pushUrl.isBlank()) {
            errorText.setKeyAndArgs("railroad.git.remotes.actions.dialog.error_push_url_required");
            confirmButton.setDisable(true);
            return;
        }

        errorText.setKeyAndArgs("");
        confirmButton.setDisable(false);
    }

    private boolean isRemoteNameAvailable(String proposedName, String currentName) {
        String normalized = proposedName.trim();
        List<GitRemote> remotes = gitManager.getRemotes();
        return remotes.stream().noneMatch(remote -> {
            if (remote.name().equals(currentName))
                return false;

            return remote.name().equals(normalized);
        });
    }

    private GitRemote resolveDefaultRemote() {
        String upstreamName = gitManager.getUpstream().map(GitUpstream::remoteName).orElse(null);
        if (upstreamName == null)
            return null;

        return gitManager.getRemotes()
            .stream()
            .filter(remote -> remote.name().equals(upstreamName))
            .findFirst()
            .orElse(null);
    }

    private GitRemote resolveRemoteForAction() {
        if (selectedRemote != null) {
            GitRemote refreshedRemote = gitManager.getRemotes().stream()
                .filter(remote -> remote.name().equals(selectedRemote.name()))
                .findFirst()
                .orElse(null);
            if (refreshedRemote != null)
                return refreshedRemote;
        }

        return resolveDefaultRemote();
    }

    /**
     * Runs the existing edit action for the selected remote.
     */
    public void editSelectedRemote() {
        GitRemote remote = resolveRemoteForAction();
        if (remote != null) {
            openEditRemoteDialog(remote);
        }
    }

    /**
     * Runs the existing remove action for the selected remote.
     */
    public void removeSelectedRemote() {
        GitRemote remote = resolveRemoteForAction();
        if (remote != null) {
            openRemoveRemoteDialog(remote);
        }
    }

    /**
     * Runs the existing open browser action for the selected remote.
     */
    public void openSelectedRemoteInBrowser() {
        GitRemote remote = resolveRemoteForAction();
        if (remote == null)
            return;

        String url = gitManager.getRemoteUrls(remote).stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(urlCandidate -> !urlCandidate.isBlank())
            .filter(urlCandidate -> urlCandidate.startsWith("http://") || urlCandidate.startsWith("https://"))
            .findFirst()
            .orElse(null);
        if (url != null) {
            try {
                DesktopUtils.openUrl(url);
            } catch (Exception exception) {
                Railroad.LOGGER.error("Failed to open remote URL in browser: {}", url, exception);
            }
        }
    }

    /**
     * Checks whether a remote can be resolved for an action.
     *
     * @return whether a remote is available
     */
    public boolean hasSelectedRemote() {
        return resolveRemoteForAction() != null;
    }
}
