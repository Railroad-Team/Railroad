package dev.railroadide.railroad.ui.id;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.railroad.gradle.ui.GradleToolsPane;
import dev.railroadide.railroad.gradle.ui.deps.GradleDependenciesPane;
import dev.railroadide.railroad.gradle.ui.task.GradleTasksPane;
import dev.railroadide.railroad.ide.projectexplorer.ProjectExplorerPane;
import dev.railroadide.railroad.ide.runconfig.ui.RunConfigurationEditorPane;
import dev.railroadide.railroad.ide.ui.IDEPane;
import dev.railroadide.railroad.ide.ui.IDEStatusBarPane;
import dev.railroadide.railroad.ide.ui.IDETopBarPane;
import dev.railroadide.railroad.ide.ui.git.branches.GitBranchesPane;
import dev.railroadide.railroad.ide.ui.git.commit.GitCommitActionsPane;
import dev.railroadide.railroad.ide.ui.git.commit.GitCommitChangesPane;
import dev.railroadide.railroad.ide.ui.git.commit.GitCommitHeaderPane;
import dev.railroadide.railroad.ide.ui.git.commit.GitCommitPane;
import dev.railroadide.railroad.ide.ui.git.commit.details.GitCommitDetailsPane;
import dev.railroadide.railroad.ide.ui.git.commit.list.GitCommitListHeaderPane;
import dev.railroadide.railroad.ide.ui.git.commit.list.GitCommitListPane;
import dev.railroadide.railroad.ide.ui.git.commit.list.GitCommitListViewPane;
import dev.railroadide.railroad.ide.ui.git.diff.GitDiffPane;
import dev.railroadide.railroad.ide.ui.git.overview.GitOverviewHeaderPane;
import dev.railroadide.railroad.ide.ui.git.overview.GitOverviewIdentityPane;
import dev.railroadide.railroad.ide.ui.git.overview.GitOverviewPane;
import dev.railroadide.railroad.ide.ui.git.overview.GitOverviewRecentCommitsPane;
import dev.railroadide.railroad.ide.ui.git.remote.GitRemoteActionsPane;
import dev.railroadide.railroad.ide.ui.git.remote.GitRemoteDetailsPane;
import dev.railroadide.railroad.ide.ui.git.remote.GitRemotesListPane;
import dev.railroadide.railroad.ide.ui.git.remote.GitRemotesPane;
import dev.railroadide.railroad.ide.ui.git.stash.GitStashPane;
import dev.railroadide.railroad.ide.ui.git.sync.*;
import dev.railroadide.railroad.plugin.ui.PluginsPane;
import dev.railroadide.railroad.project.onboarding.creation.ui.ProjectCreationPane;
import dev.railroadide.railroad.project.onboarding.ui.BasicOnboardingUI;
import dev.railroadide.railroad.settings.ui.SettingsPane;
import dev.railroadide.railroad.welcome.WelcomeHeaderPane;
import dev.railroadide.railroad.welcome.WelcomeLeftPane;
import dev.railroadide.railroad.welcome.WelcomePane;
import dev.railroadide.railroad.welcome.WelcomeProjectsPane;
import dev.railroadide.railroad.welcome.imports.WelcomeImportProjectsPane;
import dev.railroadide.railroad.welcome.project.ui.NewProjectPane;
import javafx.scene.Node;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class UIIds {
    private static final Set<UIId<?>> IDS = new HashSet<>();

    public static class Welcome {
        public static final UIId<WelcomePane> WELCOME = internal("welcome_pane", WelcomePane.class);
        public static final UIId<WelcomeLeftPane> WELCOME_LEFT = internal("welcome_left_pane", WelcomeLeftPane.class);
        public static final UIId<WelcomeHeaderPane> WELCOME_HEADER = internal("welcome_header_pane", WelcomeHeaderPane.class);
        public static final UIId<WelcomeProjectsPane> WELCOME_PROJECTS = internal("welcome_projects_pane", WelcomeProjectsPane.class);
        public static final UIId<NewProjectPane> NEW_PROJECT = internal("new_project_pane", NewProjectPane.class);
        public static final UIId<WelcomeImportProjectsPane> WELCOME_IMPORT_PROJECTS = internal("welcome_import_projects_pane", WelcomeImportProjectsPane.class);
    }

    public static class IDE {
        public static final UIId<IDEPane> IDE = internal("ide_pane", IDEPane.class);
        public static final UIId<DetachableTabPane> IDE_LEFT_DOCK = internal("ide_left_dock", DetachableTabPane.class);
        public static final UIId<DetachableTabPane> IDE_CODE_EDITOR_DOCK = internal("ide_code_editor_dock", DetachableTabPane.class);
        public static final UIId<DetachableTabPane> IDE_GIT_EDITOR_DOCK = internal("ide_git_editor_dock", DetachableTabPane.class);
        public static final UIId<DetachableTabPane> IDE_RIGHT_DOCK = internal("ide_right_dock", DetachableTabPane.class);
        public static final UIId<DetachableTabPane> IDE_BOTTOM_DOCK = internal("ide_bottom_dock", DetachableTabPane.class);
        public static final UIId<ProjectExplorerPane> PROJECT_EXPLORER = internal("project_explorer", ProjectExplorerPane.class);
        public static final UIId<IDETopBarPane> IDE_TOP_BAR = internal("ide_top_bar", IDETopBarPane.class);
        public static final UIId<IDEStatusBarPane> IDE_STATUS_BAR = internal("status_bar", IDEStatusBarPane.class);
        public static final UIId<RunConfigurationEditorPane> RUN_CONFIGURATION_EDITOR = internal("run_configuration_editor", RunConfigurationEditorPane.class);
    }

    public static class Git {
        public static final UIId<GitBranchesPane> GIT_BRANCHES = internal("git_branches", GitBranchesPane.class);

        public static final UIId<GitCommitPane> GIT_COMMIT = internal("git_commit", GitCommitPane.class);
        public static final UIId<GitCommitHeaderPane> GIT_COMMIT_HEADER = internal("git_commit_header", GitCommitHeaderPane.class);
        public static final UIId<GitCommitChangesPane> GIT_COMMIT_CHANGES = internal("git_commit_changes", GitCommitChangesPane.class);
        public static final UIId<GitCommitActionsPane> GIT_COMMIT_ACTIONS = internal("git_commit_actions", GitCommitActionsPane.class);
        public static final UIId<GitCommitDetailsPane> GIT_COMMIT_DETAILS = internal("git_commit_details", GitCommitDetailsPane.class);

        public static final UIId<GitCommitListPane> GIT_COMMIT_LIST = internal("git_commit_list", GitCommitListPane.class);
        public static final UIId<GitCommitListHeaderPane> GIT_COMMIT_LIST_HEADER = internal("git_commit_list_header", GitCommitListHeaderPane.class);
        public static final UIId<GitCommitListViewPane> GIT_COMMIT_LIST_VIEW = internal("git_commit_list_view", GitCommitListViewPane.class);

        public static final UIId<GitOverviewPane> GIT_OVERVIEW = internal("git_overview", GitOverviewPane.class);
        public static final UIId<GitOverviewHeaderPane> GIT_OVERVIEW_HEADER = internal("git_overview_header", GitOverviewHeaderPane.class);
        public static final UIId<GitOverviewIdentityPane> GIT_OVERVIEW_IDENTITY = internal("git_overview_identity", GitOverviewIdentityPane.class);
        public static final UIId<GitOverviewRecentCommitsPane> GIT_OVERVIEW_RECENT_COMMITS = internal("git_overview_recent_commits", GitOverviewRecentCommitsPane.class);

        public static final UIId<GitRemotesPane> GIT_REMOTES = internal("git_remotes", GitRemotesPane.class);
        public static final UIId<GitRemoteActionsPane> GIT_REMOTE_ACTIONS = internal("git_remote_actions", GitRemoteActionsPane.class);
        public static final UIId<GitRemoteDetailsPane> GIT_REMOTE_DETAILS = internal("git_remote_details", GitRemoteDetailsPane.class);
        public static final UIId<GitRemotesListPane> GIT_REMOTES_LIST = internal("git_remotes_list", GitRemotesListPane.class);

        public static final UIId<GitStashPane> GIT_STASH = internal("git_stash", GitStashPane.class);

        public static final UIId<GitSyncPane> GIT_SYNC = internal("git_sync", GitSyncPane.class);
        public static final UIId<GitSyncInfoPane> GIT_SYNC_INFO = internal("git_sync_info", GitSyncInfoPane.class);
        public static final UIId<GitSyncControlsPane> GIT_SYNC_CONTROLS = internal("git_sync_controls", GitSyncControlsPane.class);
        public static final UIId<GitSyncIncomingChangesPane> GIT_SYNC_INCOMING_CHANGES = internal("git_sync_incoming_changes", GitSyncIncomingChangesPane.class);
        public static final UIId<GitSyncOutgoingChangesPane> GIT_SYNC_OUTGOING_CHANGES = internal("git_sync_outgoing_changes", GitSyncOutgoingChangesPane.class);
    }

    public static class Gradle {
        public static final UIId<GradleToolsPane> GRADLE_TOOLS = internal("gradle_tools", GradleToolsPane.class);
        public static final UIId<GradleTasksPane> GRADLE_TASKS = internal("gradle_tasks", GradleTasksPane.class);
        public static final UIId<GradleDependenciesPane> GRADLE_DEPENDENCIES = internal("gradle_dependencies", GradleDependenciesPane.class);
    }

    public static class Settings {
        public static final UIId<SettingsPane> SETTINGS = internal("settings_pane", SettingsPane.class);
        public static final UIId<PluginsPane> PLUGINS = internal("plugins_pane", PluginsPane.class);
    }

    public static class ProjectOnboarding {
        public static final UIId<BasicOnboardingUI> ONBOARDING = internal("onboarding", BasicOnboardingUI.class);
        public static final UIId<ProjectCreationPane> PROJECT_CREATION = internal("project_creation", ProjectCreationPane.class);
    }

    private UIIds() {
    }

    private static <T extends Node> UIId<T> internal(String path, Class<T> type) {
        return create("railroad:" + path, type);
    }

    public static <T extends Node> UIId<T> create(String path, Class<T> type) {
        Objects.requireNonNull(path, "Path cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        if (path.isBlank())
            throw new IllegalArgumentException("Path cannot be blank");

        if (IDS.stream().anyMatch(id -> id.path().equals(path)))
            throw new IllegalArgumentException("UIId with path '" + path + "' already exists.");

        UIId<T> id = new UIId<>(path, type);
        IDS.add(id);
        return id;
    }

    public static <T extends Node> UIId<T> createTemporary(String path, Class<T> type) {
        Objects.requireNonNull(path, "Path cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        if (path.isBlank())
            throw new IllegalArgumentException("Path cannot be blank");

        return new UIId<>(path + "_temp_" + UUID.randomUUID(), type);
    }

    public static int size() {
        return IDS.size();
    }
}
