package io.github.railroad.task;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.LinkedList;

public class TaskManager {
    private final ObservableList<Task> tasks = FXCollections.observableList(new LinkedList<>());
    private final DoubleProperty progress = new SimpleDoubleProperty(0);

    public DoubleProperty progressProperty() {
        return progress;
    }

    public void addTask(Task task) {
        tasks.add(task);
        task.progressProperty().addListener((observable, oldValue, newValue) -> updateProgress());
    }

    public void execute() {
        while (!tasks.isEmpty()) {
            Task task = tasks.removeFirst();
            task.execute();
        }
    }

    private void updateProgress() {
        int numTasks = tasks.size();

        double totalProgress = 0.0;
        if (numTasks > 0) {
            totalProgress = tasks.stream().mapToDouble(task -> task.progressProperty().get()).sum();
            totalProgress /= numTasks * 100; // Normalize to percentage
        }

        progress.set(totalProgress);
    }
}
