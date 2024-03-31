package io.github.railroad.task;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Task {
    private final StringProperty name = new SimpleStringProperty();
    private final IntegerProperty progress = new SimpleIntegerProperty(0);
    private final TaskExecution execution;

    public Task(String name, TaskExecution execution) {
        this.name.set(name);
        this.execution = execution;
    }

    public StringProperty nameProperty() {
        return name;
    }

    public IntegerProperty progressProperty() {
        return progress;
    }

    public void execute() {
        execution.execute(this.progress);
    }
}
