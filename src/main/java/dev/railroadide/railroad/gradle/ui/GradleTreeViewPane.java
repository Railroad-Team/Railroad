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
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.TreeView;
import javafx.scene.layout.StackPane;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class GradleTreeViewPane<T> extends RRVBox {
    private final ObservableList<T> elements = FXCollections.observableArrayList();

    private final TreeView<GradleTreeElement> treeView = new TreeView<>();
    private final MFXProgressSpinner loadingSpinner = new MFXProgressSpinner();
    private final StackPane loadingContainer = new StackPane(loadingSpinner);
    private final AtomicBoolean isLoading = new AtomicBoolean(true);

    public GradleTreeViewPane(Project project) {
        super();
        getStyleClass().add("gradle-tool-content-pane");

        loadingSpinner.getStyleClass().add("gradle-tool-loading-spinner");
        loadingContainer.setAlignment(Pos.CENTER);
        loadingContainer.prefHeightProperty().bind(heightProperty());
        loadingContainer.prefWidthProperty().bind(widthProperty());

        treeView.getStyleClass().add("gradle-tasks-tree-view");
        treeView.setShowRoot(false);
        treeView.setCellFactory(param -> new GradleTreeCell());
        treeView.prefHeightProperty().bind(heightProperty());
        elements.addListener((ListChangeListener<? super T>) change -> Platform.runLater(() -> {
            GradleTreeBuilder<T> treeBuilder = createTreeBuilder();
            treeView.setRoot(treeBuilder.buildTree(project, elements));
        }));

        updateLoadingState();

        GradleModelService modelService = project.getGradleManager().getGradleModelService();
        modelService.refreshModel(true);
        modelService.addListener(new GradleModelListener() {
            @Override
            public void modelReloadStarted() {
                isLoading.set(true);
                updateLoadingState();
            }

            @Override
            public void modelReloadSucceeded(GradleBuildModel model) {
                isLoading.set(false);
                elements.setAll(getElementsFromModel(modelService, model));
                updateLoadingState();
            }

            @Override
            public void modelReloadFailed(Throwable error) {
                Railroad.LOGGER.error("Failed to reload Gradle model", error);
                isLoading.set(false);
                updateLoadingState();
            }
        });
    }

    protected abstract GradleTreeBuilder<T> createTreeBuilder();

    protected abstract Collection<T> getElementsFromModel(GradleModelService modelService, GradleBuildModel model);

    protected void updateLoadingState() {
        Platform.runLater(() -> {
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
        });
    }
}
