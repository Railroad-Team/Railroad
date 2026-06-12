package dev.railroadide.railroad.gradle.ui;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.gradle.model.GradleBuildModel;
import dev.railroadide.railroad.gradle.model.GradleModelListener;
import dev.railroadide.railroad.gradle.service.GradleModelService;
import dev.railroadide.railroad.gradle.ui.tree.GradleTreeCell;
import dev.railroadide.railroad.gradle.ui.tree.GradleTreeElement;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRVBox;
import io.github.palexdev.materialfx.controls.MFXProgressSpinner;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public abstract class GradleTreeViewPane<T> extends RRVBox {
    private final Project project;
    private final TreeView<GradleTreeElement> treeView = new TreeView<>();
    private final MFXProgressSpinner loadingSpinner = new MFXProgressSpinner();
    private final StackPane loadingContainer = new StackPane(loadingSpinner);
    private final AtomicBoolean isLoading = new AtomicBoolean(true);
    private final AtomicLong reloadGeneration = new AtomicLong();

    public GradleTreeViewPane(Project project) {
        super();
        this.project = project;
        getStyleClass().add("gradle-tool-content-pane");

        loadingSpinner.getStyleClass().add("gradle-tool-loading-spinner");
        loadingContainer.setAlignment(Pos.CENTER);
        loadingContainer.prefHeightProperty().bind(heightProperty());
        loadingContainer.prefWidthProperty().bind(widthProperty());

        treeView.getStyleClass().add("gradle-tasks-tree-view");
        treeView.setShowRoot(false);
        treeView.setCellFactory(param -> new GradleTreeCell());
        treeView.prefHeightProperty().bind(heightProperty());

        updateLoadingState();

        GradleModelService modelService = project.getGradleManager().getGradleModelService();
        modelService.addListener(new GradleModelListener() {
            @Override
            public void modelReloadStarted() {
                reloadGeneration.incrementAndGet();
                isLoading.set(true);
                updateLoadingState();
            }

            @Override
            public void modelReloadSucceeded(GradleBuildModel model) {
                rebuildTree(modelService, model);
            }

            @Override
            public void modelReloadFailed(Throwable error) {
                Railroad.LOGGER.error("Failed to reload Gradle model", error);
                isLoading.set(false);
                updateLoadingState();
            }
        });

        modelService.getCachedModel().ifPresent(model -> rebuildTree(modelService, model));
    }

    protected abstract GradleTreeBuilder<T> createTreeBuilder();

    protected abstract Collection<T> getElementsFromModel(GradleModelService modelService, GradleBuildModel model);

    private void rebuildTree(GradleModelService modelService, GradleBuildModel model) {
        long generation = reloadGeneration.get();
        try {
            List<T> elements = new ArrayList<>(getElementsFromModel(modelService, model));
            TreeItem<GradleTreeElement> root = createTreeBuilder().buildTree(project, elements);
            Platform.runLater(() -> {
                if (generation != reloadGeneration.get()) {
                    return;
                }
                treeView.setRoot(root);
                isLoading.set(false);
                updateLoadingState();
            });
        } catch (Throwable error) {
            Railroad.LOGGER.error("Failed to build Gradle tree", error);
            if (generation == reloadGeneration.get()) {
                isLoading.set(false);
                updateLoadingState();
            }
        }
    }

    protected void updateLoadingState() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::updateLoadingState);
            return;
        }

        ObservableList<Node> children = getChildren();
        if (isLoading.get()) {
            if (!children.contains(loadingContainer)) {
                children.clear();
                children.add(loadingContainer);
            }
        } else {
            if (!children.contains(treeView)) {
                children.clear();
                children.add(treeView);
            }
        }
    }
}
