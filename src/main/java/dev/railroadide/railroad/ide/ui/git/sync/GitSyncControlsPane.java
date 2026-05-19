package dev.railroadide.railroad.ide.ui.git.sync;

import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.vcs.git.GitManager;
import dev.railroadide.railroad.vcs.git.remote.GitRemote;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

public class GitSyncControlsPane extends RRVBox {
    public GitSyncControlsPane(GitManager gitManager) {
        getStyleClass().add("git-sync-controls-pane-root");

        var remoteLabel = new LocalizedLabel("railroad.git.sync.controls.remote");
        remoteLabel.getStyleClass().add("git-sync-controls-remote-label");

        ObservableList<GitRemote> remotes = FXCollections.observableArrayList();
        ComboBox<GitRemote> remoteComboBox = new ComboBox<>(remotes);
        remoteComboBox.getStyleClass().add("git-sync-controls-remote-combobox");
        remoteComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(GitRemote remote) {
                if(remote == null)
                    return L18n.localize("railroad.git.sync.controls.remote.none");

                return remote.name();
            }

            @Override
            public GitRemote fromString(String string) {
                return remotes.stream()
                    .filter(remote -> remote.name().equals(string))
                    .findFirst()
                    .orElse(null);
            }
        });
        remoteComboBox.setOnAction($ -> {
            GitRemote selectedRemote = remoteComboBox.getSelectionModel().getSelectedItem();
            if (selectedRemote != null) {
                gitManager.setCurrentRemote(selectedRemote);
            }
        });

        var upstreamBranchLabel = new LocalizedLabel("railroad.git.sync.controls.upstreamBranch");
        upstreamBranchLabel.getStyleClass().add("git-sync-controls-upstream-branch-label");

        ComboBox<String> upstreamBranchComboBox = new ComboBox<>();
        upstreamBranchComboBox.getStyleClass().add("git-sync-controls-upstream-branch-combobox");
        upstreamBranchComboBox.setDisable(true);
        upstreamBranchComboBox.setOnAction($ -> {
            String selectedBranch = upstreamBranchComboBox.getSelectionModel().getSelectedItem();
            if (selectedBranch != null) {
                gitManager.setCurrentUpstreamBranch(selectedBranch);
            }
        });

        var remoteVbox = new RRVBox(remoteLabel, remoteComboBox);
        remoteVbox.getStyleClass().add("git-sync-controls-remote-vbox");
        var upstreamBranchVbox = new RRVBox(upstreamBranchLabel, upstreamBranchComboBox);
        upstreamBranchVbox.getStyleClass().add("git-sync-controls-upstream-branch-vbox");

        var fetchButton = new RRButton("railroad.git.sync.controls.fetch", FontAwesomeSolid.SYNC);
        fetchButton.getStyleClass().add("git-sync-controls-fetch-button");
        fetchButton.setOnAction($ -> gitManager.fetch());

        var pullButton = new RRButton("railroad.git.sync.controls.pull", FontAwesomeSolid.DOWNLOAD);
        pullButton.getStyleClass().add("git-sync-controls-pull-button");
        pullButton.setOnAction($ -> gitManager.pull());

        var pushButton = new RRButton("railroad.git.sync.controls.push", FontAwesomeSolid.PAPER_PLANE);
        pushButton.setVariant(ButtonVariant.SECONDARY);
        pushButton.getStyleClass().add("git-sync-controls-push-button");
        pushButton.setOnAction($ -> gitManager.push());

        var buttonsHbox = new RRHBox(2, fetchButton, pullButton, pushButton);
        buttonsHbox.getStyleClass().add("git-sync-controls-buttons-hbox");

        getChildren().addAll(remoteVbox, upstreamBranchVbox, buttonsHbox);

        Runnable refreshRemotes = () -> {
            remotes.setAll(gitManager.getRemotes());
            remoteComboBox.getSelectionModel().select(gitManager.getCurrentRemote());
        };
        refreshRemotes.run();
        gitManager.repoStatusProperty().addListener((obs, oldStatus, newStatus) -> refreshRemotes.run());
    }
}
