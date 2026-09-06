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
import dev.railroadide.railroad.ide.ui.setup.RunControlsPane;
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

/**
 * Creates typed UI identifiers and groups Railroad's built-in pane identifiers by feature.
 * Registered paths are unique across node types. Built-in groups register their constants when
 * initialized; temporary identifiers are not added to the registry.
 */
public final class UIIds {
    private static final Set<UIId<?>> IDS = new HashSet<>();

    /** Identifiers for the welcome screen and its project-selection workflows. */
    public static class Welcome {
        /** Creates a stateless grouping instance; identifiers are available through static constants. */
        public Welcome() {
        }

        /** The root welcome screen. */
        public static final UIId<WelcomePane> WELCOME = internal("welcome_pane", WelcomePane.class);
        /** The welcome screen's left-hand navigation pane. */
        public static final UIId<WelcomeLeftPane> WELCOME_LEFT = internal("welcome_left_pane", WelcomeLeftPane.class);
        /** The welcome screen header. */
        public static final UIId<WelcomeHeaderPane> WELCOME_HEADER = internal("welcome_header_pane",
            WelcomeHeaderPane.class);
        /** The welcome screen's project list. */
        public static final UIId<WelcomeProjectsPane> WELCOME_PROJECTS = internal("welcome_projects_pane",
            WelcomeProjectsPane.class);
        /** The welcome workflow for starting a new project. */
        public static final UIId<NewProjectPane> NEW_PROJECT = internal("new_project_pane", NewProjectPane.class);
        /** The welcome workflow for importing existing projects. */
        public static final UIId<WelcomeImportProjectsPane> WELCOME_IMPORT_PROJECTS = internal(
            "welcome_import_projects_pane", WelcomeImportProjectsPane.class);
    }

    /** Identifiers for the IDE shell, docking areas, and core editing tools. */
    public static class IDE {
        /** Creates a stateless grouping instance; identifiers are available through static constants. */
        public IDE() {
        }

        /** The root IDE pane for an open project. */
        public static final UIId<IDEPane> IDE = internal("ide_pane", IDEPane.class);
        /** The IDE's left docking area. */
        public static final UIId<DetachableTabPane> IDE_LEFT_DOCK = internal("ide_left_dock", DetachableTabPane.class);
        /** The docking area containing code editor tabs. */
        public static final UIId<DetachableTabPane> IDE_CODE_EDITOR_DOCK = internal("ide_code_editor_dock",
            DetachableTabPane.class);
        /** The docking area containing Git editor tabs. */
        public static final UIId<DetachableTabPane> IDE_GIT_EDITOR_DOCK = internal("ide_git_editor_dock",
            DetachableTabPane.class);
        /** The IDE's right docking area. */
        public static final UIId<DetachableTabPane> IDE_RIGHT_DOCK = internal("ide_right_dock",
            DetachableTabPane.class);
        /** The IDE's bottom docking area. */
        public static final UIId<DetachableTabPane> IDE_BOTTOM_DOCK = internal("ide_bottom_dock",
            DetachableTabPane.class);
        /** The project file and directory explorer. */
        public static final UIId<ProjectExplorerPane> PROJECT_EXPLORER = internal("project_explorer",
            ProjectExplorerPane.class);
        /**
         * Identifies the run toolbar for the current project.
         */
        public static final UIId<RunControlsPane> RUN_CONTROLS = internal("run_controls", RunControlsPane.class);
        /** The IDE's top bar. */
        public static final UIId<IDETopBarPane> IDE_TOP_BAR = internal("ide_top_bar", IDETopBarPane.class);
        /** The IDE's status bar. */
        public static final UIId<IDEStatusBarPane> IDE_STATUS_BAR = internal("status_bar", IDEStatusBarPane.class);
        /** The editor for project run configurations. */
        public static final UIId<RunConfigurationEditorPane> RUN_CONFIGURATION_EDITOR = internal(
            "run_configuration_editor", RunConfigurationEditorPane.class);
    }

    /** Identifiers for Git repository inspection, commit, remote, stash, and synchronization panes. */
    public static class Git {
        /** Creates a stateless grouping instance; identifiers are available through static constants. */
        public Git() {
        }

        /** The repository branch management pane. */
        public static final UIId<GitBranchesPane> GIT_BRANCHES = internal("git_branches", GitBranchesPane.class);

        /** The root pane for preparing a Git commit. */
        public static final UIId<GitCommitPane> GIT_COMMIT = internal("git_commit", GitCommitPane.class);
        /** The header of the commit preparation pane. */
        public static final UIId<GitCommitHeaderPane> GIT_COMMIT_HEADER = internal("git_commit_header",
            GitCommitHeaderPane.class);
        /** The changed-file selection area used when preparing a commit. */
        public static final UIId<GitCommitChangesPane> GIT_COMMIT_CHANGES = internal("git_commit_changes",
            GitCommitChangesPane.class);
        /** The controls for performing commit actions. */
        public static final UIId<GitCommitActionsPane> GIT_COMMIT_ACTIONS = internal("git_commit_actions",
            GitCommitActionsPane.class);
        /** The pane showing details of a selected commit. */
        public static final UIId<GitCommitDetailsPane> GIT_COMMIT_DETAILS = internal("git_commit_details",
            GitCommitDetailsPane.class);

        /** The container for the repository's commit history. */
        public static final UIId<GitCommitListPane> GIT_COMMIT_LIST = internal("git_commit_list",
            GitCommitListPane.class);
        /** The header and controls above the commit history list. */
        public static final UIId<GitCommitListHeaderPane> GIT_COMMIT_LIST_HEADER = internal("git_commit_list_header",
            GitCommitListHeaderPane.class);
        /** The list view displaying repository commits. */
        public static final UIId<GitCommitListViewPane> GIT_COMMIT_LIST_VIEW = internal("git_commit_list_view",
            GitCommitListViewPane.class);

        /** The repository overview container. */
        public static final UIId<GitOverviewPane> GIT_OVERVIEW = internal("git_overview", GitOverviewPane.class);
        /** The repository overview header. */
        public static final UIId<GitOverviewHeaderPane> GIT_OVERVIEW_HEADER = internal("git_overview_header",
            GitOverviewHeaderPane.class);
        /** The Git identity section of the repository overview. */
        public static final UIId<GitOverviewIdentityPane> GIT_OVERVIEW_IDENTITY = internal("git_overview_identity",
            GitOverviewIdentityPane.class);
        /** The recent commits section of the repository overview. */
        public static final UIId<GitOverviewRecentCommitsPane> GIT_OVERVIEW_RECENT_COMMITS = internal(
            "git_overview_recent_commits", GitOverviewRecentCommitsPane.class);

        /** The container for repository remote management. */
        public static final UIId<GitRemotesPane> GIT_REMOTES = internal("git_remotes", GitRemotesPane.class);
        /** The controls for acting on repository remotes. */
        public static final UIId<GitRemoteActionsPane> GIT_REMOTE_ACTIONS = internal("git_remote_actions",
            GitRemoteActionsPane.class);
        /** The details of the selected repository remote. */
        public static final UIId<GitRemoteDetailsPane> GIT_REMOTE_DETAILS = internal("git_remote_details",
            GitRemoteDetailsPane.class);
        /** The list of configured repository remotes. */
        public static final UIId<GitRemotesListPane> GIT_REMOTES_LIST = internal("git_remotes_list",
            GitRemotesListPane.class);

        /** The repository stash management pane. */
        public static final UIId<GitStashPane> GIT_STASH = internal("git_stash", GitStashPane.class);

        /** The container for repository synchronization. */
        public static final UIId<GitSyncPane> GIT_SYNC = internal("git_sync", GitSyncPane.class);
        /** The synchronization status and information pane. */
        public static final UIId<GitSyncInfoPane> GIT_SYNC_INFO = internal("git_sync_info", GitSyncInfoPane.class);
        /** The controls for synchronizing the repository with its remote. */
        public static final UIId<GitSyncControlsPane> GIT_SYNC_CONTROLS = internal("git_sync_controls",
            GitSyncControlsPane.class);
        /** The incoming changes section of repository synchronization. */
        public static final UIId<GitSyncIncomingChangesPane> GIT_SYNC_INCOMING_CHANGES = internal(
            "git_sync_incoming_changes", GitSyncIncomingChangesPane.class);
        /** The outgoing changes section of repository synchronization. */
        public static final UIId<GitSyncOutgoingChangesPane> GIT_SYNC_OUTGOING_CHANGES = internal(
            "git_sync_outgoing_changes", GitSyncOutgoingChangesPane.class);
    }

    /** Identifiers for Gradle build tools, tasks, and dependency inspection. */
    public static class Gradle {
        /** Creates a stateless grouping instance; identifiers are available through static constants. */
        public Gradle() {
        }

        /** The container for Gradle tools. */
        public static final UIId<GradleToolsPane> GRADLE_TOOLS = internal("gradle_tools", GradleToolsPane.class);
        /** The Gradle task browser. */
        public static final UIId<GradleTasksPane> GRADLE_TASKS = internal("gradle_tasks", GradleTasksPane.class);
        /** The Gradle dependency browser. */
        public static final UIId<GradleDependenciesPane> GRADLE_DEPENDENCIES = internal("gradle_dependencies",
            GradleDependenciesPane.class);
    }

    /** Identifiers for application preferences and plugin management. */
    public static class Settings {
        /** Creates a stateless grouping instance; identifiers are available through static constants. */
        public Settings() {
        }

        /** The application settings pane. */
        public static final UIId<SettingsPane> SETTINGS = internal("settings_pane", SettingsPane.class);
        /** The plugin management pane. */
        public static final UIId<PluginsPane> PLUGINS = internal("plugins_pane", PluginsPane.class);
    }

    /** Identifiers for project onboarding and creation. */
    public static class ProjectOnboarding {
        /** Creates a stateless grouping instance; identifiers are available through static constants. */
        public ProjectOnboarding() {
        }

        /** The basic onboarding interface for configuring a project. */
        public static final UIId<BasicOnboardingUI> ONBOARDING = internal("onboarding", BasicOnboardingUI.class);
        /** The pane that presents project creation. */
        public static final UIId<ProjectCreationPane> PROJECT_CREATION = internal("project_creation",
            ProjectCreationPane.class);
    }

    private UIIds() {
    }

    private static <T extends Node> UIId<T> internal(String path, Class<T> type) {
        return create("railroad:" + path, type);
    }

    /**
     * Creates and registers an identifier with a path not already present in the registry.
     * Path uniqueness applies even when the existing identifier has a different node type.
     *
     * @param <T> the node type associated with the identifier
     * @param path the nonblank identifier path
     * @param type the class of nodes identified by this path
     * @return the registered identifier
     * @throws NullPointerException if {@code path} or {@code type} is {@code null}
     * @throws IllegalArgumentException if the path is blank or already registered
     */
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

    /**
     * Creates an unregistered identifier by appending {@code _temp_} and a random UUID to a base path.
     * Temporary identifiers do not reserve registry paths or contribute to {@link #size()}.
     *
     * @param <T> the node type associated with the identifier
     * @param path the nonblank base path for the temporary identifier
     * @param type the class of nodes identified by the generated path
     * @return a temporary identifier containing the base path and a random suffix
     * @throws NullPointerException if {@code path} or {@code type} is {@code null}
     * @throws IllegalArgumentException if the base path is blank
     */
    public static <T extends Node> UIId<T> createTemporary(String path, Class<T> type) {
        Objects.requireNonNull(path, "Path cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        if (path.isBlank())
            throw new IllegalArgumentException("Path cannot be blank");

        return new UIId<>(path + "_temp_" + UUID.randomUUID(), type);
    }

    /**
     * Returns the number of identifiers registered so far. Built-in groups contribute their
     * identifiers only after initialization, and temporary identifiers are excluded.
     *
     * @return the current registered identifier count
     */
    public static int size() {
        return IDS.size();
    }
}
