package dev.railroadide.railroad.ide.ui;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.gradle.ui.GradleToolsPane;
import dev.railroadide.railroad.ide.projectexplorer.ProjectExplorerPane;
import dev.railroadide.railroad.ide.ui.git.branches.GitBranchesPane;
import dev.railroadide.railroad.ide.ui.git.commit.GitCommitPane;
import dev.railroadide.railroad.ide.ui.git.commit.details.GitCommitDetailsPane;
import dev.railroadide.railroad.ide.ui.git.commit.list.GitCommitListPane;
import dev.railroadide.railroad.ide.ui.git.diff.GitDiffPane;
import dev.railroadide.railroad.ide.ui.git.overview.GitOverviewPane;
import dev.railroadide.railroad.ide.ui.git.remote.GitRemotesPane;
import dev.railroadide.railroad.ide.ui.git.stash.GitStashPane;
import dev.railroadide.railroad.ide.ui.git.sync.GitSyncPane;
import dev.railroadide.railroad.ide.ui.setup.PaneIconBarFactory;
import dev.railroadide.railroad.ide.ui.setup.TerminalFactory;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.FacetDetectedEvent;
import dev.railroadide.railroad.project.facet.Facet;
import dev.railroadide.railroad.project.facet.FacetManager;
import dev.railroadide.railroad.settings.keybinds.KeybindContexts;
import dev.railroadide.railroad.settings.keybinds.KeybindHandler;
import dev.railroadide.railroad.ui.RRBorderPane;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.utility.icon.RailroadBrandsIcon;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import org.kordamp.ikonli.fontawesome6.FontAwesomeBrands;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.util.Map;
import java.util.Objects;

public final class IDEPane extends RRBorderPane {
    private final Project project;

    public IDEPane(Project project) {
        this.project = Objects.requireNonNull(project, "Project cannot be null");


        setTop(new IDETopBarPane(project));

        var leftPane = createLeftPane();
        var rightPane = new DetachableTabPane();
        Services.UI_MANAGER.assignWhileAttached(UIIds.IDE.IDE_RIGHT_DOCK, rightPane);
        var editorPane = createEditorPane();
        var consolePane = createBottomPane();

        var centerBottomSplit = new SplitPane(editorPane, consolePane);
        centerBottomSplit.setOrientation(Orientation.VERTICAL);
        centerBottomSplit.setDividerPositions(0.75);

        var mainSplit = new SplitPane(leftPane, centerBottomSplit, rightPane);
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        mainSplit.setDividerPositions(0.15, 0.85);
        setCenter(mainSplit);

        configureGradlePane(rightPane, mainSplit);
        setLeft(createLeftIconBar(leftPane, mainSplit));
        setBottom(createBottomBar(consolePane, centerBottomSplit));

        KeybindHandler.registerCapture(KeybindContexts.of("railroad:ide"), this);

        Services.UI_MANAGER.assignWhileAttached(UIIds.IDE.IDE, this);
    }

    private DetachableTabPane createLeftPane() {
        var pane = new DetachableTabPane();
        pane.addTab("Project", new ProjectExplorerPane(project));
        pane.addTab("Git Commit", new GitCommitPane(project));
        pane.addTab("Git Overview", new GitOverviewPane(project));
        pane.addTab("Git Commit List", new GitCommitListPane(project));
        pane.addTab("Git Branches", new GitBranchesPane(project));
        pane.addTab("Git Remotes", new GitRemotesPane(project));
        pane.addTab("Git Sync", new GitSyncPane(project));
        pane.addTab("Git Stash", new GitStashPane(project));

        Services.UI_MANAGER.assignWhileAttached(UIIds.IDE.IDE_LEFT_DOCK, pane);
        return pane;
    }

    private DetachableTabPane createEditorPane() {
        var pane = new DetachableTabPane();
        pane.addTab("Welcome", new IDEWelcomePane());

        var gitDiffPane = new GitDiffPane(project);
        var gitDiffTab = pane.addTab("Git Diff", gitDiffPane);
        gitDiffTab.textProperty().bind(gitDiffPane.titleProperty());

        var gitCommitDetailsPane = new GitCommitDetailsPane(project);
        var gitCommitDetailsTab = pane.addTab("Git Commit Details", gitCommitDetailsPane);
        gitCommitDetailsTab.textProperty().bind(gitCommitDetailsPane.titleProperty());

        Services.UI_MANAGER.assignWhileAttached(UIIds.IDE.IDE_EDITOR_DOCK, pane);
        return pane;
    }

    private DetachableTabPane createBottomPane() {
        var pane = new DetachableTabPane();
        pane.addTab("Console", new ConsolePane());
        pane.addTab("Terminal", TerminalFactory.create(project.getPath()));

        Services.UI_MANAGER.assignWhileAttached(UIIds.IDE.IDE_BOTTOM_DOCK, pane);
        return pane;
    }

    private void configureGradlePane(DetachableTabPane rightPane, SplitPane mainSplit) {
        if (project.hasFacet(FacetManager.GRADLE)) {
            openGradleTab(project.getFacet(FacetManager.GRADLE).orElseThrow(), rightPane, mainSplit);
        }

        Railroad.EVENT_BUS.subscribe(FacetDetectedEvent.class, event -> {
            if (event.project() == project) {
                openGradleTab(event.facet(), rightPane, mainSplit);
            }
        });
    }

    private void openGradleTab(Facet<?> facet, DetachableTabPane rightPane, SplitPane mainSplit) {
        Platform.runLater(() -> {
            if (facet.getType() != FacetManager.GRADLE || rightPane.getTabs().stream()
                .anyMatch(tab -> tab.getContent() instanceof GradleToolsPane)) {
                return;
            }

            rightPane.addTab("Gradle", new GradleToolsPane(project));
            setRight(PaneIconBarFactory.create(
                rightPane,
                mainSplit,
                Orientation.VERTICAL,
                2,
                Map.of("Gradle", RailroadBrandsIcon.GRADLE.getDescription())
            ));
        });
    }

    private static Node createLeftIconBar(DetachableTabPane leftPane, SplitPane mainSplit) {
        return PaneIconBarFactory.create(
            leftPane,
            mainSplit,
            Orientation.VERTICAL,
            0,
            Map.of(
                "Project", FontAwesomeSolid.FOLDER.getDescription(),
                "Git Commit", FontAwesomeBrands.USB.getDescription(),
                "Git Overview", FontAwesomeSolid.HOME.getDescription(),
                "Git Commit List", FontAwesomeSolid.LIST.getDescription(),
                "Git Branches", FontAwesomeSolid.CODE_BRANCH.getDescription(),
                "Git Remotes", FontAwesomeSolid.GLOBE.getDescription(),
                "Git Sync", FontAwesomeSolid.SYNC.getDescription(),
                "Git Stash", FontAwesomeSolid.BOX.getDescription()
            )
        );
    }

    private static RRVBox createBottomBar(DetachableTabPane consolePane, SplitPane centerBottomSplit) {
        var bottomBar = new RRVBox();
        var bottomIcons = PaneIconBarFactory.create(
            consolePane,
            centerBottomSplit,
            Orientation.HORIZONTAL,
            1,
            Map.of(
                "Console", FontAwesomeSolid.PLAY_CIRCLE.getDescription(),
                "Terminal", FontAwesomeSolid.TERMINAL.getDescription()
            )
        );
        bottomBar.getChildren().addAll(bottomIcons, new IDEStatusBarPane());
        return bottomBar;
    }
}
