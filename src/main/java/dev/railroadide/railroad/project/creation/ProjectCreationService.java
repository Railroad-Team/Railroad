package dev.railroadide.railroad.project.creation;

import dev.railroadide.railroad.project.ProjectContext;
import dev.railroadide.railroad.project.ProjectCreationPipeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

/**
 * Runs a project creation pipeline as a JavaFX background service and exposes its progress and status.
 */
public class ProjectCreationService extends Service<Void> {
    private final ProjectCreationPipeline creator;
    private final ProjectContext ctx;
    private final ObservableList<String> log = FXCollections.observableArrayList();

    /**
     * Creates a service for the supplied pipeline and project context.
     *
     * @param creator the pipeline to execute
     * @param ctx the context of the project being created
     */
    public ProjectCreationService(ProjectCreationPipeline creator, ProjectContext ctx) {
        this.creator = creator;
        this.ctx = ctx;
    }

    /**
     * Creates a task that forwards pipeline progress and status to this service.
     *
     * @return a new task that executes the configured pipeline
     */
    @Override
    protected Task<Void> createTask() {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                var reporter = new ProgressReporter() {
                    @Override
                    public void progress(int i, int total) {
                        updateProgress(i, total);
                    }

                    @Override
                    public void info(String line) {
                        updateMessage(line);
                        log.add(line);
                    }

                    @Override
                    public void setArg(Object... args) {
                        // TODO: could setValue on a StringProperty for i18n args
                    }
                };

                creator.run(ctx, reporter);
                updateMessage("railroad.project.creation.task.completed");
                return null;
            }
        };
    }
}
