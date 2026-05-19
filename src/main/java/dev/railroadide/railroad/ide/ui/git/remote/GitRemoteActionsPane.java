package dev.railroadide.railroad.ide.ui.git.remote;

import dev.railroadide.railroad.utility.DesktopUtils;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRTextField;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.localized.LocalizedText;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
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

public class GitRemoteActionsPane extends RRVBox {
    private final GitManager gitManager;

    private final RRButton editRemoteButton;
    private final RRButton removeRemoteButton;
    private final RRButton fetchButton;
    private final RRButton pruneButton;
    private final RRButton openInBrowserButton;

    private GitRemote selectedRemote;

    public GitRemoteActionsPane(GitManager gitManager) {
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
        openInBrowserButton = new RRButton("railroad.git.remotes.actions.button.open_in_browser", FontAwesomeSolid.GLOBE);

        var primaryActionsBox = new RRHBox(fetchAllButton, pruneAllButton, addRemoteButton);
        primaryActionsBox.getStyleClass().add("git-remotes-actions-primary-actions");
        primaryActionsBox.setAlignment(Pos.CENTER);

        var secondaryActionsBox = new RRHBox(editRemoteButton, removeRemoteButton, fetchButton, pruneButton, openInBrowserButton);
        secondaryActionsBox.getStyleClass().add("git-remotes-actions-secondary-actions");
        secondaryActionsBox.setAlignment(Pos.CENTER);

        getChildren().addAll(primaryActionsBox, secondaryActionsBox);
        setAlignment(Pos.TOP_CENTER);

        fetchAllButton.setOnAction(event -> gitManager.fetchAllRemotes());
        pruneAllButton.setOnAction(event -> gitManager.pruneAllRemotes());
        addRemoteButton.setOnAction(event -> openAddRemoteDialog());

        editRemoteButton.setOnAction(event -> {
            GitRemote remote = resolveRemoteForAction();
            if (remote != null) {
                openEditRemoteDialog(remote);
            }
        });

        removeRemoteButton.setOnAction(event -> {
            GitRemote remote = resolveRemoteForAction();
            if (remote != null) {
                openRemoveRemoteDialog(remote);
            }
        });

        fetchButton.setOnAction(event -> gitManager.fetch());
        pruneButton.setOnAction(event -> gitManager.gc());
        openInBrowserButton.setOnAction(event -> {
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
        });

        updateActions(resolveDefaultRemote());
    }

    public void updateActions(GitRemote remote) {
        selectedRemote = remote;
        boolean hasRemote = remote != null;

        editRemoteButton.setDisable(!hasRemote);
        removeRemoteButton.setDisable(!hasRemote);
        fetchButton.setDisable(!hasRemote);
        pruneButton.setDisable(!hasRemote);
        openInBrowserButton.setDisable(!hasRemote);
    }

    private void openAddRemoteDialog() {
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

        content.getChildren().addAll(nameLabel, nameField, fetchUrlLabel, fetchUrlField, pushUrlLabel, pushUrlField, errorText);

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

        Runnable validator = () -> validateAddRemoteInput(nameField, fetchUrlField, pushUrlField, errorText, confirmButton);
        validator.run();
        nameField.textProperty().addListener((obs, oldText, newText) -> validator.run());
        fetchUrlField.textProperty().addListener((obs, oldText, newText) -> validator.run());
        pushUrlField.textProperty().addListener((obs, oldText, newText) -> validator.run());
    }

    private void openEditRemoteDialog(GitRemote remote) {
        var content = new RRVBox();
        content.getStyleClass().add("git-remote-edit-dialog-content");

        var currentRemoteText = new LocalizedText("railroad.git.remotes.actions.edit_dialog.current_remote", remote.name());

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

        content.getChildren().addAll(currentRemoteText, nameLabel, nameField, fetchUrlLabel, fetchUrlField, pushUrlLabel, pushUrlField, errorText);

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

        Runnable validator = () -> validateEditRemoteInput(remote.name(), nameField, fetchUrlField, pushUrlField, errorText, confirmButton);
        validator.run();
        nameField.textProperty().addListener((obs, oldText, newText) -> validator.run());
        fetchUrlField.textProperty().addListener((obs, oldText, newText) -> validator.run());
        pushUrlField.textProperty().addListener((obs, oldText, newText) -> validator.run());
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

        cancelButton.setOnAction($ -> dialog.close());
        confirmButton.setOnAction($ -> {
            if (remote.name().equals(confirmationField.getText())) {
                gitManager.removeRemote(remote.name());
                dialog.close();
            }
        });

        confirmationField.textProperty().addListener((obs, oldText, newText) -> {
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
        return !newName.isBlank() && !fetchUrl.isBlank() && !pushUrl.isBlank() && isRemoteNameAvailable(newName, currentName);
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
}
