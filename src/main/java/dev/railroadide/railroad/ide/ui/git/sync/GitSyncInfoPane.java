package dev.railroadide.railroad.ide.ui.git.sync;

import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.localized.LocalizedComboBox;
import dev.railroadide.railroad.ui.localized.LocalizedText;
import dev.railroadide.railroad.vcs.git.GitManager;
import dev.railroadide.railroad.vcs.git.remote.GitUpstream;
import dev.railroadide.railroad.vcs.git.status.GitRepoStatus;
import dev.railroadide.railroad.vcs.git.util.GitPullStrategy;
import dev.railroadide.railroad.vcs.git.util.GitPushStrategy;
import javafx.scene.control.Label;

public class GitSyncInfoPane extends RRVBox {
    public GitSyncInfoPane(GitManager gitManager) {
        getStyleClass().add("git-sync-info-pane");

        var trackingInfoPane = new GitSyncTrackingInfoPane(gitManager);
        var aheadBehindInfoPane = new GitSyncAheadBehindInfoPane(gitManager);
        var strategyPane = new GitSyncStrategyPane(gitManager);

        getChildren().addAll(
            trackingInfoPane,
            aheadBehindInfoPane,
            strategyPane
        );
    }

    private static class GitSyncTrackingInfoPane extends RRVBox {
        private final Label branchChip = new Label();
        private final Label upstreamChip = new Label();

        public GitSyncTrackingInfoPane(GitManager gitManager) {
            getStyleClass().add("git-sync-tracking-info-pane");

            var trackingTitle = new LocalizedText("railroad.git.sync.tracking.title");
            trackingTitle.getStyleClass().add("git-sync-tracking-info-title");

            var branchText = new LocalizedText("railroad.git.sync.tracking.branch");
            branchText.getStyleClass().add("git-sync-tracking-info-label");
            branchChip.getStyleClass().add("git-sync-tracking-info-chip");
            branchChip.getStyleClass().add("git-local-branch-upstream-chip");

            var upstreamText = new LocalizedText("railroad.git.sync.tracking.upstream");
            upstreamText.getStyleClass().add("git-sync-tracking-info-label");
            upstreamChip.getStyleClass().add("git-sync-tracking-info-chip");
            upstreamChip.getStyleClass().add("git-local-branch-upstream-chip");

            var infoBox = new RRHBox(2);
            infoBox.getChildren().addAll(
                branchText, branchChip,
                upstreamText, upstreamChip
            );
            infoBox.getStyleClass().add("git-sync-tracking-info-box");

            getChildren().addAll(
                trackingTitle,
                infoBox
            );

            update(gitManager.getRepoStatus(), gitManager.getUpstream().orElse(null));
            gitManager.repoStatusProperty().addListener((obs, oldStatus, newStatus) ->
                update(newStatus, gitManager.getUpstream().orElse(null)));
        }

        private void update(GitRepoStatus status, GitUpstream upstream) {
            branchChip.setText(status != null ? status.branch() : "");
            upstreamChip.setText(upstream != null
                ? upstream.branchName()
                : L18n.localize("railroad.git.sync.tracking.upstream.none"));
        }
    }

    private static class GitSyncAheadBehindInfoPane extends RRVBox {
        private final RRHBox aheadBehindBox = new RRHBox(8);

        public GitSyncAheadBehindInfoPane(GitManager gitManager) {
            getStyleClass().add("git-sync-ahead-behind-info-pane");

            var aheadBehindTitle = new LocalizedText("railroad.git.sync.aheadBehind.title");
            aheadBehindTitle.getStyleClass().add("git-sync-ahead-behind-info-title");

            aheadBehindBox.getStyleClass().add("git-sync-ahead-behind-info-box");

            getChildren().addAll(
                aheadBehindTitle,
                aheadBehindBox
            );

            update(gitManager.getRepoStatus());
            gitManager.repoStatusProperty().addListener((obs, oldStatus, newStatus) -> update(newStatus));
        }

        private void update(GitRepoStatus status) {
            aheadBehindBox.getChildren().clear();

            int aheadCount = status != null ? (int) status.ahead() : 0;
            int behindCount = status != null ? (int) status.behind() : 0;

            if (aheadCount > 0) {
                var aheadText = new LocalizedText("railroad.git.sync.aheadBehind.ahead", aheadCount);
                aheadText.getStyleClass().add("git-sync-ahead-behind-info-ahead");
                aheadBehindBox.getChildren().add(aheadText);
            }

            if (behindCount > 0) {
                var behindText = new LocalizedText("railroad.git.sync.aheadBehind.behind", behindCount);
                behindText.getStyleClass().add("git-sync-ahead-behind-info-behind");
                aheadBehindBox.getChildren().add(behindText);
            }

            if (aheadCount == 0 && behindCount == 0) {
                var upToDateText = new LocalizedText("railroad.git.sync.aheadBehind.upToDate");
                upToDateText.getStyleClass().add("git-sync-ahead-behind-info-up-to-date");
                aheadBehindBox.getChildren().add(upToDateText);
            }
        }
    }

    private static class GitSyncStrategyPane extends RRVBox {
        private final LocalizedComboBox<GitPullStrategy> pullStrategyComboBox = new LocalizedComboBox<>(GitPullStrategy::getLocalizationKey);
        private final LocalizedComboBox<GitPushStrategy> pushStrategyComboBox = new LocalizedComboBox<>(GitPushStrategy::getLocalizationKey);

        public GitSyncStrategyPane(GitManager gitManager) {
            getStyleClass().add("git-sync-strategy-pane");

            var strategyTitle = new LocalizedText("railroad.git.sync.strategy.title");
            strategyTitle.getStyleClass().add("git-sync-strategy-title");

            var pullStrategyHbox = new RRHBox(2);
            pullStrategyHbox.getStyleClass().add("git-sync-strategy-pull-box");
            var pullStrategyLabel = new LocalizedText("railroad.git.sync.strategy.pull");
            pullStrategyLabel.getStyleClass().add("git-sync-strategy-label");
            pullStrategyComboBox.getItems().addAll(GitPullStrategy.values());
            pullStrategyComboBox.setOnAction(e -> {
                GitPullStrategy selectedStrategy = pullStrategyComboBox.getSelectionModel().getSelectedItem();
                if (selectedStrategy != null) {
                    gitManager.setPullStrategy(selectedStrategy);
                }
            });
            pullStrategyComboBox.getStyleClass().add("git-sync-strategy-combobox");
            pullStrategyHbox.getChildren().addAll(
                pullStrategyLabel, pullStrategyComboBox
            );

            var pushStrategyHbox = new RRHBox(2);
            pushStrategyHbox.getStyleClass().add("git-sync-strategy-push-box");
            var pushStrategyLabel = new LocalizedText("railroad.git.sync.strategy.push");
            pushStrategyLabel.getStyleClass().add("git-sync-strategy-label");
            pushStrategyComboBox.getItems().addAll(GitPushStrategy.values());
            pushStrategyComboBox.setOnAction(e -> {
                GitPushStrategy selectedStrategy = pushStrategyComboBox.getSelectionModel().getSelectedItem();
                if (selectedStrategy != null) {
                    gitManager.setPushStrategy(selectedStrategy);
                }
            });
            pushStrategyComboBox.getStyleClass().add("git-sync-strategy-combobox");
            pushStrategyHbox.getChildren().addAll(
                pushStrategyLabel, pushStrategyComboBox
            );

            getChildren().addAll(
                strategyTitle,
                pullStrategyHbox,
                pushStrategyHbox
            );

            updateSelections(gitManager);
            gitManager.repoStatusProperty().addListener((obs, oldStatus, newStatus) -> updateSelections(gitManager));
        }

        private void updateSelections(GitManager gitManager) {
            pullStrategyComboBox.getSelectionModel().select(gitManager.getPullStrategy());
            pushStrategyComboBox.getSelectionModel().select(gitManager.getPushStrategy());
        }
    }
}
