package dev.railroadide.core.project.creation;

import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.ProjectCreationPipeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

public class ProjectCreationService extends Service<Void> {
    private final ProjectCreationPipeline creator;
    private final ProjectContext ctx;
    private final ObservableList<String> log = FXCollections.observableArrayList();

    public ProjectCreationService(ProjectCreationPipeline creator, ProjectContext ctx) {
        this.creator = creator;
        this.ctx = ctx;
    }

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
